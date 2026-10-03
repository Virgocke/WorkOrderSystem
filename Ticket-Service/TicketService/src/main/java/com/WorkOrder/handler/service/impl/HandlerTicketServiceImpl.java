package com.WorkOrder.handler.service.impl;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.handler.dto.HandlerTicketPageDto;
import com.WorkOrder.handler.dto.TicketNoteDto;
import com.WorkOrder.handler.dto.TransferTicketDto;
import com.WorkOrder.handler.mapper.AssignmentRecordMapper;
import com.WorkOrder.handler.mapper.HandlerProfileMapper;
import com.WorkOrder.handler.model.AssignmentRecord;
import com.WorkOrder.handler.service.HandlerTicketService;
import com.WorkOrder.model.Result;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.model.search.TicketSearchDocument;
import com.WorkOrder.model.search.TicketSearchPage;
import com.WorkOrder.model.search.TicketSearchQuery;
import com.WorkOrder.model.ticket.TicketResponse;
import com.WorkOrder.model.user.UserProfile;
import com.WorkOrder.ticket.converter.TicketConverter;
import com.WorkOrder.ticket.enums.TicketStatusEnum;
import com.WorkOrder.ticket.feignclient.UserFeignClient;
import com.WorkOrder.ticket.feignclient.TicketSearchFeignClient;
import com.WorkOrder.ticket.mapper.TicketMapper;
import com.WorkOrder.ticket.mapper.TicketOperationLogMapper;
import com.WorkOrder.ticket.mapper.TicketStatusHistoryMapper;
import com.WorkOrder.ticket.model.TicketOperationLog;
import com.WorkOrder.ticket.model.TicketStatusHistory;
import com.WorkOrder.ticket.model.Tickets;
import com.WorkOrder.ticket.messaging.TicketTransferredEventPublisher;
import com.WorkOrder.ticket.messaging.TicketEscalatedEventPublisher;
import com.WorkOrder.ticket.messaging.TicketResolvedEventPublisher;
import com.WorkOrder.ticket.service.TicketResponseAttachmentEnricher;
import com.WorkOrder.ticket.service.AssignmentScoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Virgor
 * @date 2026年09月16日 03:27
 * @description 处理人服务实现类
 */
@Service
@RequiredArgsConstructor
public class HandlerTicketServiceImpl implements HandlerTicketService {

    /** 允许处理人执行转派或升级的工单状态。 */
    private static final Set<String> ACTIVE_HANDLER_STATUSES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    TicketStatusEnum.PENDING_RESPONSE.name(),
                    TicketStatusEnum.PROCESSING.name())));

    /** 处理人档案数据访问入口。 */
    private final HandlerProfileMapper handlerProfileMapper;
    /** 工单主表数据访问入口。 */
    private final TicketMapper ticketMapper;
    /** 工单状态历史数据访问入口。 */
    private final TicketStatusHistoryMapper ticketStatusHistoryMapper;
    /** 工单操作日志数据访问入口。 */
    private final TicketOperationLogMapper ticketOperationLogMapper;
    /** 转派分配记录数据访问入口。 */
    private final AssignmentRecordMapper assignmentRecordMapper;
    /** 用于校验目标处理人的用户服务客户端。 */
    private final UserFeignClient userFeignClient;
    /** 工单响应中的附件信息补全器。 */
    private final TicketResponseAttachmentEnricher ticketResponseAttachmentEnricher;
    /** 转派事实的事务性事件发布器。 */
    private final TicketTransferredEventPublisher ticketTransferredEventPublisher;
    /** 升级事实的事务性事件发布器。 */
    private final TicketEscalatedEventPublisher ticketEscalatedEventPublisher;
    /** 解决事实的事务性事件发布器。 */
    private final TicketResolvedEventPublisher ticketResolvedEventPublisher;
    private final AssignmentScoreService assignmentScoreService;
    /** 按当前处理人权限查询 ES 候选工单。 */
    private final TicketSearchFeignClient ticketSearchFeignClient;

    /**
     * 查询当前处理人工单：空关键词由 MySQL 分页，有关键词由 ES 排序分页后批量回表。
     * 回表复核当前归属及状态，索引滞后时当前页可能不足，但保留 ES 命中总数。
     * @param handlerId 从认证信息取得的当前处理人 ID
     * @param handlerTicketPageDto 分页、关键词、状态和排序条件，不修改原始对象
     * @return 带真实查询总数的工单分页响应
     * @throws IllegalArgumentException 查询参数不合法
     * @throws IllegalStateException 搜索调用失败或返回数据不合法
     */
    @Override
    public PageResult<TicketResponse> getHandlerTicket(Long handlerId, HandlerTicketPageDto handlerTicketPageDto) {
        Assert.isTrue(handlerId != null && handlerId > 0, "处理人 ID 必须为正数");
        validateHandlerListQuery(handlerTicketPageDto);
        if (!StringUtils.hasText(handlerTicketPageDto.getKeyword())) {
            return queryHandlerPageFromMysql(handlerId, handlerTicketPageDto);
        }

        TicketSearchQuery query = new TicketSearchQuery();
        query.setHandlerId(handlerId.toString());
        query.setKeyword(handlerTicketPageDto.getKeyword().trim());
        query.setStatus(normalizeHandlerStatus(handlerTicketPageDto.getStatus()));
        query.setSort(normalizeHandlerSort(handlerTicketPageDto.getSort()));
        query.setPage(Math.toIntExact(handlerTicketPageDto.getPage()));
        query.setPageSize(Math.toIntExact(handlerTicketPageDto.getPageSize()));
        try {
            Result<TicketSearchPage> result = ticketSearchFeignClient.esSearchForHandler(query);
            if (result == null || result.getCode() != SystemExceptionEnum.SUCCESS.getCode()
                    || result.getData() == null) {
                throw new IllegalStateException("搜索服务未返回成功的分页结果");
            }
            TicketSearchPage data = result.getData();
            if (data.getPage() != query.getPage() || data.getPageSize() != query.getPageSize()
                    || data.getTotal() < 0 || data.getRecords().size() > data.getPageSize()) {
                throw new IllegalStateException("搜索服务返回的分页结果不合法");
            }
            List<TicketResponse> responses = loadHandlerSearchResponses(
                    handlerId, handlerTicketPageDto, data.getRecords());
            return new PageResult<>(ticketResponseAttachmentEnricher.enrichAll(responses), data.getTotal(),
                    handlerTicketPageDto.getPage(), handlerTicketPageDto.getPageSize());
        } catch (IOException exception) {
            throw new IllegalStateException("处理人工单搜索失败", exception);
        }
    }

    /** 空关键词使用 MySQL 排序分页，并保留数据库查询总数和空页语义。 */
    private PageResult<TicketResponse> queryHandlerPageFromMysql(Long handlerId, HandlerTicketPageDto query) {
        LambdaQueryWrapper<Tickets> queryWrapper = handlerTicketFilters(handlerId, query);
        String sort = normalizeHandlerSort(query.getSort());
        if ("deadline".equals(sort)) {
            queryWrapper.orderByAsc(Tickets::getResponseDeadline);
        } else if ("priority".equals(sort)) {
            queryWrapper.orderByDesc(Tickets::getPriority);
        }
        queryWrapper.orderByDesc(Tickets::getCreatedAt).orderByDesc(Tickets::getId);
        Page<Tickets> page = ticketMapper.selectPage(new Page<>(query.getPage(), query.getPageSize()), queryWrapper);
        List<TicketResponse> responses = page.getRecords() == null ? Collections.emptyList()
                : page.getRecords().stream().map(TicketConverter::toResponse).collect(Collectors.toList());
        return new PageResult<>(ticketResponseAttachmentEnricher.enrichAll(responses), page.getTotal(),
                query.getPage(), query.getPageSize());
    }

    /** 只批量读取当前 ES 页的 ID，复核当前归属和状态，再按 ES 顺序转换响应。 */
    private List<TicketResponse> loadHandlerSearchResponses(
            Long handlerId, HandlerTicketPageDto query, List<TicketSearchDocument> records) {
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = records.stream().map(this::handlerSearchTicketId).distinct().collect(Collectors.toList());
        List<Tickets> currentTickets = ticketMapper.selectList(handlerTicketFilters(handlerId, query)
                .in(Tickets::getId, ids));
        Map<Long, Tickets> ticketsById = currentTickets.stream()
                .collect(Collectors.toMap(Tickets::getId, ticket -> ticket));
        return ids.stream().map(ticketsById::get).filter(Objects::nonNull)
                .map(TicketConverter::toResponse).collect(Collectors.toList());
    }

    /** MySQL 分页和 ES 回表共用条件，当前处理人限制始终由服务端追加。 */
    private LambdaQueryWrapper<Tickets> handlerTicketFilters(Long handlerId, HandlerTicketPageDto query) {
        LambdaQueryWrapper<Tickets> filters = new LambdaQueryWrapper<Tickets>()
                .eq(Tickets::getHandlerId, handlerId);
        String status = normalizeHandlerStatus(query.getStatus());
        if (status != null) {
            filters.eq(Tickets::getStatus, status);
        }
        return filters;
    }

    /** 将 ES 字符串 ID 转为数据库正数 ID，损坏的投影作为搜索服务错误处理。 */
    private Long handlerSearchTicketId(TicketSearchDocument record) {
        if (record == null || !StringUtils.hasText(record.getTicketId())) {
            throw new IllegalStateException("搜索结果缺少工单 ID");
        }
        try {
            long id = Long.parseLong(record.getTicketId());
            if (id <= 0) {
                throw new IllegalStateException("搜索结果包含非法工单 ID");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("搜索结果包含非法工单 ID", exception);
        }
    }

    /** 校验公共参数，有关键词时限制 ES 基础分页窗口，避免溢出和非法排序。 */
    private static void validateHandlerListQuery(HandlerTicketPageDto query) {
        Assert.notNull(query, "查询条件不能为空");
        Assert.isTrue(query.getPage() != null && query.getPage() >= 1, "页码必须大于等于 1");
        Assert.isTrue(query.getPageSize() != null && query.getPageSize() >= 1 && query.getPageSize() <= 100,
                "每页数量必须介于 1 和 100 之间");
        Assert.isTrue(query.getKeyword() == null || query.getKeyword().length() <= 500,
                "搜索关键字不能超过 500 个字符");
        String status = normalizeHandlerStatus(query.getStatus());
        if (status != null) {
            TicketStatusEnum.valueOf(status);
        }
        String sort = normalizeHandlerSort(query.getSort());
        Assert.isTrue("deadline".equals(sort) || "priority".equals(sort) || "createdAt".equals(sort),
                "排序仅支持 deadline、priority 或 createdAt");
        if (StringUtils.hasText(query.getKeyword())) {
            Assert.isTrue(query.getPage() <= 10000 / query.getPageSize(),
                    "基础分页仅支持前 10000 条，请缩小筛选范围");
        } else {
            Assert.isTrue(query.getPage() - 1 <= Long.MAX_VALUE / query.getPageSize(), "分页范围过大");
        }
    }

    /** 将空白和 all 状态统一为不限制状态，其余状态编码去除首尾空白。 */
    private static String normalizeHandlerStatus(String value) {
        String status = StringUtils.hasText(value) ? value.trim() : null;
        return "all".equalsIgnoreCase(status) ? null : status;
    }

    /** 未指定排序时沿用响应截止时间升序，显式排序值去除首尾空白。 */
    private static String normalizeHandlerSort(String value) {
        return StringUtils.hasText(value) ? value.trim() : "deadline";
    }

    /**
     * 处理人首次响应
     * @param ticketId 工单ID
     * @param operatorId 当前操作人ID
     * @param operatorRole 当前登录角色，由后端认证信息取得
     * @param clientIp 客户端IP
     * @return 工单响应结果
     */
    @Override
    @Transactional
    public TicketResponse firstResponse(Long ticketId, Long operatorId, String operatorRole, String clientIp) {
        Tickets ticket = ticketMapper.selectById(ticketId);
        // 如果工单不存在，则抛出异常
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }

        // 如果当前登录用户不是管理员，且不是处理人，则抛出异常
        boolean isAdmin = "ADMIN".equals(operatorRole);
        if (!isAdmin && (!"HANDLER".equals(operatorRole)
                || !Objects.equals(operatorId, ticket.getHandlerId()))) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }


        // 如果工单状态不是待响应，则抛出异常
        if (!TicketStatusEnum.PENDING_RESPONSE.name().equals(ticket.getStatus())) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_NOT_ALLOWED);
        }


        // 保存工单状态历史
        String oldStatus = ticket.getStatus();
        // 设置新的工单状态
        ticket.setStatus(TicketStatusEnum.PROCESSING.name());
        ticket.setFirstResponseAt(LocalDateTime.now());

        // 仅首次响应可以变更状态，避免重复请求产生重复日志。
        int update = ticketMapper.update(null, new LambdaUpdateWrapper<Tickets>()
                .eq(Tickets::getId, ticketId)
                .eq(Tickets::getStatus, oldStatus)
                .eq(!isAdmin, Tickets::getHandlerId, operatorId)
                .set(Tickets::getStatus, ticket.getStatus())
                .set(Tickets::getFirstResponseAt, ticket.getFirstResponseAt()));
        if (update != 1) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 保存工单状态历史
        boolean saveTicketStatusHistory =
                saveTicketStatusHistory(ticket, oldStatus, operatorId, "首次响应","RESPOND");

        if (!saveTicketStatusHistory){
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 与工单状态、状态历史在同一事务中保存操作日志。
        if (!saveTicketOperationLog(
                ticket,
                "RESPOND",
                operatorId,
                operatorRole,
                clientIp,
                "首次响应工单，开始处理")) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        return ticketResponseAttachmentEnricher.enrich(TicketConverter.toResponse(ticket));
    }

    /**
     * 处理人解决工单
     * @param ticketId 工单ID
     * @param solution 解决方案
     * @param operatorId 当前操作人ID
     * @param operatorRole 当前登录角色，由后端认证信息取得
     * @param clientIp 客户端IP
     * @return 工单解决结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketResponse resolveTicket(
            Long ticketId,
            String solution,
            Long operatorId,
            String operatorRole,
            String clientIp) {

        Tickets ticket = ticketMapper.selectById(ticketId);

        // 如果工单不存在，则抛出异常
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }

        // 保存工单状态历史
        String oldStatus = ticket.getStatus();

        // 如果当前登录用户不是管理员，且不是处理人，则抛出异常
        boolean isAdmin = "ADMIN".equals(operatorRole);
        if (!isAdmin && (!"HANDLER".equals(operatorRole)
                || !Objects.equals(operatorId, ticket.getHandlerId()))) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        // 如果工单状态不是处理中，则抛出异常
        if (!TicketStatusEnum.PROCESSING.name().equals(ticket.getStatus())) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_NOT_ALLOWED);
        }

        // 设置新的工单状态
        ticket.setStatus(TicketStatusEnum.RESOLVED.name());
        ticket.setResolvedAt(LocalDateTime.now());
        // 解决业绩固定归当时负责的处理人；若曾转派，当前处理人即接手人。
        ticket.setResolvedByHandlerId(ticket.getHandlerId());

        // 仅允许从处理中变更状态，避免并发解决产生重复日志与事件。
        int update = ticketMapper.update(null, new LambdaUpdateWrapper<Tickets>()
                .eq(Tickets::getId, ticketId)
                .eq(Tickets::getStatus, oldStatus)
                .eq(!isAdmin, Tickets::getHandlerId, operatorId)
                .set(Tickets::getStatus, ticket.getStatus())
                .set(Tickets::getResolvedAt, ticket.getResolvedAt())
                .set(Tickets::getResolvedByHandlerId, ticket.getResolvedByHandlerId()));

        if (update != 1) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 保存工单状态历史
        boolean saveTicketStatusHistory =
                saveTicketStatusHistory(ticket, oldStatus, operatorId, solution, "RESOLVE");

        if (!saveTicketStatusHistory){
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 与工单状态、状态历史在同一事务中保存操作日志。
        TicketOperationLog resolutionLog = saveTicketOperationLogAndReturn(
                ticket,
                "RESOLVE",
                operatorId,
                operatorRole,
                clientIp,
                "解决工单");
        if (resolutionLog == null || resolutionLog.getId() == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        ticketResolvedEventPublisher.publish(ticket, oldStatus, resolutionLog);

        return ticketResponseAttachmentEnricher.enrich(TicketConverter.toResponse(ticket));
    }

    /** 条件更新处理人后，同事务保存转派记录、审计日志与事件快照。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketResponse transferTicketToOtherHandler(
            Long ticketId,
            TransferTicketDto transferTicketDto,
            Long operatorId,
            String operatorRole,
            String clientIp) {
        Tickets ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }

        // 如果当前登录用户不是管理员，且不是处理人，则抛出异常
        boolean isAdmin = "ADMIN".equals(operatorRole);
        if (!isAdmin && (!"HANDLER".equals(operatorRole)
                || !Objects.equals(operatorId, ticket.getHandlerId()))) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        if (!ACTIVE_HANDLER_STATUSES.contains(ticket.getStatus()) || ticket.getHandlerId() == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_NOT_ALLOWED);
        }

        if (transferTicketDto == null || transferTicketDto.getToHandlerId() == null
                || transferTicketDto.getReason() == null
                || transferTicketDto.getReason().trim().isEmpty()) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        String transferReason = transferTicketDto.getReason().trim();

        // 取出旧处理人ID
        Long oldHandlerId = ticket.getHandlerId();
        // 如果旧处理人ID与新处理人ID相同，则抛出异常
        if (Objects.equals(oldHandlerId, transferTicketDto.getToHandlerId())){
            throw new SystemException(SystemExceptionEnum.TICKET_TRANSFER_SAME_HANDLER);
        }

        // 校验新处理人ID是否存在
        Result<UserProfile> result = userFeignClient.getById(transferTicketDto.getToHandlerId());
        if (result == null || result.getData() == null) {
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }
        // 校验新处理人ID是否状态正常
        UserProfile toHandler = result.getData();
        if (toHandler.getRole() != 1 || toHandler.getStatus() != 1) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_DISABLED);
        }

        ticketMapper.lockHandlerProfile(transferTicketDto.getToHandlerId());

        LocalDateTime transferredAt = LocalDateTime.now();

        // 设置新的处理人ID，转派不改变工单状态和既有 SLA 截止时间。
        ticket.setHandlerId(transferTicketDto.getToHandlerId());
        ticket.setAssignedAt(transferredAt);

        int update = ticketMapper.update(null, new LambdaUpdateWrapper<Tickets>()
                .eq(Tickets::getId, ticketId)
                // 同时核对旧处理人与原状态，避免并发转派或状态流转被覆盖。
                .eq(Tickets::getHandlerId, oldHandlerId)
                .eq(Tickets::getStatus, ticket.getStatus())
                .set(Tickets::getHandlerId,transferTicketDto.getToHandlerId())
                .set(Tickets::getAssignedAt, transferredAt));

        // 失败则抛出异常
        if (update != 1) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 保存工单状态历史
        boolean saveTicketStatusHistory =
                saveTicketStatusHistory(ticket,
                        ticket.getStatus(),
                        operatorId,
                        transferReason,
                        "TRANSFER");

        if (!saveTicketStatusHistory){
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 与工单状态、状态历史在同一事务中保存操作日志。
        TicketOperationLog transferLog = saveTicketOperationLogAndReturn(
                ticket,
                "TRANSFER",
                operatorId,
                operatorRole,
                clientIp,
                "转交工单");
        if (transferLog == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 保存转交记录
        AssignmentRecord assignmentRecord = new AssignmentRecord();
        assignmentRecord.setTicketId(ticketId);
        assignmentRecord.setHandlerId(transferTicketDto.getToHandlerId());


        assignmentScoreService.fill(ticketId, transferTicketDto.getToHandlerId(), 0, assignmentRecord);


        assignmentRecord.setAssignedBy("MANUAL");
        assignmentRecord.setCreatedAt(LocalDateTime.now());
        int insert = assignmentRecordMapper.insert(assignmentRecord);
        if (insert != 1) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }

        // 与处理人更新、审计记录和分配记录在同一事务中写入 Outbox。
        ticketTransferredEventPublisher.publish(
                ticket,
                oldHandlerId,
                operatorId,
                operatorRole,
                transferredAt,
                transferReason,
                transferLog,
                assignmentRecord);

        return ticketResponseAttachmentEnricher.enrich(TicketConverter.toResponse(ticket));
    }

    /** 仅当前处理人可逐级升级活动工单，并同事务发布升级事实。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketResponse escalateTicket(
            Long ticketId,
            String reason,
            Long operatorId,
            String operatorRole,
            String clientIp) {

        Tickets ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }

        if (!"HANDLER".equals(operatorRole)) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        // 如果当前工单的处理人不是当前处理人，则抛出异常
        if (!Objects.equals(operatorId, ticket.getHandlerId())) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        if (!ACTIVE_HANDLER_STATUSES.contains(ticket.getStatus())) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_NOT_ALLOWED);
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        String escalationReason = reason.trim();

        int escalatedLevel = ticket.getEscalatedLevel();
        if (escalatedLevel >= 3) {
            throw new SystemException(SystemExceptionEnum.TICKET_ESCALATED_LEVEL_MAX);
        }
        if (escalatedLevel < 0) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        LocalDateTime escalatedAt = LocalDateTime.now();
        ticket.setEscalatedLevel(escalatedLevel + 1);
        ticket.setSlaStatus("ESCALATED");

        int update = ticketMapper.update(null, new LambdaUpdateWrapper<Tickets>()
                .eq(Tickets::getId, ticketId)
                .eq(Tickets::getHandlerId, operatorId)
                .eq(Tickets::getStatus, ticket.getStatus())
                .eq(Tickets::getEscalatedLevel, escalatedLevel)
                .set(Tickets::getEscalatedLevel, ticket.getEscalatedLevel())
                .set(Tickets::getSlaStatus, ticket.getSlaStatus()));
        // 更新工单信息，失败则抛出异常
        if (update != 1) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 保存工单状态历史
        boolean saveTicketStatusHistory =
                saveTicketStatusHistory(
                        ticket,
                        ticket.getStatus(),
                        operatorId,
                        escalationReason,
                        "ESCALATE");

        if (!saveTicketStatusHistory){
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 与工单状态、状态历史在同一事务中保存操作日志。
        TicketOperationLog escalationLog = saveTicketOperationLogAndReturn(
                ticket,
                "ESCALATE",
                operatorId,
                operatorRole,
                clientIp,
                "升级工单");
        if (escalationLog == null || escalationLog.getId() == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 接收人快照与工单、审计记录和 Outbox 同事务固定，消费时无需回查用户服务。
        List<Long> activeAdminIds = ticketMapper.selectActiveAdminIds();
        List<Long> receiverIds = activeAdminIds == null ? Collections.emptyList() : activeAdminIds.stream()
                .filter(id -> id != null && id > 0 && !id.equals(operatorId))
                .distinct()
                .collect(Collectors.toList());
        ticketEscalatedEventPublisher.publish(ticket, escalatedLevel, escalationReason,
                escalatedAt, escalationLog, receiverIds);

        return ticketResponseAttachmentEnricher.enrich(TicketConverter.toResponse(ticket));
    }

    /**
     * 添加内部备注
     * @param ticketId 工单ID
     * @param ticketNoteDto 备注参数
     * @param operatorId 当前操作人ID
     * @param operatorRole 当前登录角色，由后端认证信息取得
     * @param clientIp 客户端IP
     * @return 是否添加成功
     */
    @Override
    @Transactional
    public Boolean setTicketNote(
            Long ticketId,
            TicketNoteDto ticketNoteDto,
            Long operatorId,
            String operatorRole,
            String clientIp) {

        Tickets ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }

        // 如果当前登录用户不是处理人，则抛出异常
        boolean isHandler = "HANDLER".equals(operatorRole);
        if (!isHandler){
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        // 如果当前工单的处理人不是当前处理人，则抛出异常
        if (!operatorId.equals(ticket.getHandlerId())){
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        // 与工单状态、状态历史在同一事务中保存操作日志。
        if (!saveTicketOperationLog(
                ticket,
                "INTERNAL_NOTE",
                operatorId,
                operatorRole,
                clientIp,
                "添加内部备注")) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        return true;
    }

    /**
     * 保存工单状态历史
     * @param ticket 工单
     * @param oldStatus 旧状态
     * @param handlerId 处理人ID
     * @param remark 备注
     * @return 是否保存成功
     */
    private boolean saveTicketStatusHistory(
            Tickets ticket,
            String oldStatus,
            Long handlerId,
            String remark,
            String event) {
        TicketStatusHistory ticketStatusHistory = new TicketStatusHistory();
        ticketStatusHistory.setTicketId(ticket.getId());
        ticketStatusHistory.setFromStatus(oldStatus);
        ticketStatusHistory.setToStatus(ticket.getStatus());
        ticketStatusHistory.setEvent(event);
        ticketStatusHistory.setOperatorId(handlerId);
        ticketStatusHistory.setRemark(remark);
        int insert = ticketStatusHistoryMapper.insert(ticketStatusHistory);
        if (insert != 1) {
            return false;
        }
        return true;

    }

    /**
     * 保存工单操作日志
     * @param ticket 工单
     * @param action 操作
     * @param operatorId 操作人ID
     * @param operatorRole 操作人角色
     * @param clientIp 客户端IP
     * @param content 内容
     * @return 是否保存成功
     */
    private boolean saveTicketOperationLog(
            Tickets ticket,
            String action,
            Long operatorId,
            String operatorRole,
            String clientIp,
            String content) {
        return saveTicketOperationLogAndReturn(
                ticket, action, operatorId, operatorRole, clientIp, content) != null;
    }

    /**
     * 保存工单操作日志并返回已持久化实体，供领域事件关联审计记录。
     *
     * @param ticket 工单
     * @param action 操作类型
     * @param operatorId 操作人 ID
     * @param operatorRole 操作人角色
     * @param clientIp 客户端 IP
     * @param content 日志内容
     * @return 保存成功后的日志；写入失败时返回 null
     */
    private TicketOperationLog saveTicketOperationLogAndReturn(
            Tickets ticket,
            String action,
            Long operatorId,
            String operatorRole,
            String clientIp,
            String content) {
        TicketOperationLog ticketOperationLog = new TicketOperationLog();
        ticketOperationLog.setTicketId(ticket.getId());
        ticketOperationLog.setAction(action);
        ticketOperationLog.setOperatorId(operatorId);
        ticketOperationLog.setOperatorRole(operatorRole);
        ticketOperationLog.setIpAddress(clientIp);
        ticketOperationLog.setContent(content);
        int insert = ticketOperationLogMapper.insert(ticketOperationLog);
        if (insert != 1) {
            return null;
        }
        return ticketOperationLog;
    }
}

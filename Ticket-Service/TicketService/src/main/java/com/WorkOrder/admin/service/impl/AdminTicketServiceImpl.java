package com.WorkOrder.admin.service.impl;

import com.WorkOrder.admin.dto.CloseTicketDto;
import com.WorkOrder.admin.service.AdminTicketService;
import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.admin.dto.AdminTicketListDto;
import com.WorkOrder.handler.dto.AssignTicketDto;
import com.WorkOrder.handler.mapper.AssignmentRecordMapper;
import com.WorkOrder.handler.model.AssignmentRecord;
import com.WorkOrder.model.Result;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.model.search.TicketSearchDocument;
import com.WorkOrder.model.search.TicketSearchPage;
import com.WorkOrder.model.search.TicketSearchQuery;
import com.WorkOrder.model.ticket.TicketResponse;
import com.WorkOrder.model.user.UserProfile;
import com.WorkOrder.ticket.converter.TicketConverter;
import com.WorkOrder.ticket.enums.TicketStatusEnum;
import com.WorkOrder.ticket.feignclient.TicketSearchFeignClient;
import com.WorkOrder.ticket.feignclient.UserFeignClient;
import com.WorkOrder.ticket.mapper.TicketMapper;
import com.WorkOrder.ticket.mapper.TicketOperationLogMapper;
import com.WorkOrder.ticket.mapper.TicketStatusHistoryMapper;
import com.WorkOrder.ticket.model.TicketOperationLog;
import com.WorkOrder.ticket.model.TicketStatusHistory;
import com.WorkOrder.ticket.model.Tickets;
import com.WorkOrder.ticket.messaging.TicketAssignedEventPublisher;
import com.WorkOrder.ticket.messaging.TicketSearchChangePublisher;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Virgor
 * @date 2026年09月17日 01:55
 * @description 管理员工单服务实现类
 */
@Service
@RequiredArgsConstructor
public class AdminTicketServiceImpl implements AdminTicketService {

    private static final ZoneId TICKET_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final TicketSearchFeignClient ticketSearchFeignClient;
    private final UserFeignClient userFeignClient;
    private final TicketMapper ticketMapper;
    private final AssignmentRecordMapper assignmentRecordMapper;
    private final TicketStatusHistoryMapper ticketStatusHistoryMapper;
    private final TicketOperationLogMapper ticketOperationLogMapper;
    private final TicketResponseAttachmentEnricher ticketResponseAttachmentEnricher;
    private final TicketAssignedEventPublisher ticketAssignedEventPublisher;
    private final AssignmentScoreService assignmentScoreService;
    private final TicketSearchChangePublisher ticketSearchChangePublisher;
    
    /**
     * 查询管理员工单列表：空关键词由 MySQL 分页，有关键词由 ES 分页后批量回表。
     * 搜索页保留 ES 的命中总数；索引滞后时，复核后返回的记录可能少于每页数量。
     *
     * @param adminId 当前认证管理员的用户 ID
     * @param adminTicketListDto 分页及筛选条件，不修改传入对象
     * @return 包含完整工单响应、总数和分页参数的结果，空页也返回分页对象
     * @throws IllegalArgumentException 分页、筛选值或日期范围不合法
     * @throws IllegalStateException 搜索调用失败或返回的数据不合法
     */
    @Override
    public PageResult<TicketResponse> getTicketListForAdmin(Long adminId, AdminTicketListDto adminTicketListDto) {
        Assert.isTrue(adminId != null && adminId > 0, "管理员 ID 必须为正数");
        validateAdminListQuery(adminTicketListDto);
        Result<UserProfile> result = userFeignClient.getById(adminId);
        if (result == null || result.getCode() != SystemExceptionEnum.SUCCESS.getCode()
                || result.getData() == null) {
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }

        // 验证用户角色，确保是管理员
        UserProfile userProfile = result.getData();
        if (userProfile.getRole() != 2 || userProfile.getStatus() != 1) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        // 空关键词继续使用 MySQL，返回数据库分页总数。
        if (!StringUtils.hasText(adminTicketListDto.getKeyword())) {
            return queryAdminPageFromMysql(adminTicketListDto);
        }

        TicketSearchQuery ticketSearchQuery = getTicketSearchQuery(adminTicketListDto);

        try {
            // 封装 Feign 调用，并检查响应是否成功。
            Result<TicketSearchPage> ticketSearchPageResult = ticketSearchFeignClient.esSearch(ticketSearchQuery);
            if (ticketSearchPageResult == null
                    || ticketSearchPageResult.getCode() != SystemExceptionEnum.SUCCESS.getCode()
                    || ticketSearchPageResult.getData() == null) {
                throw new IllegalStateException("搜索服务未返回成功的分页结果");
            }
            TicketSearchPage data = ticketSearchPageResult.getData();
            if (data.getPage() != ticketSearchQuery.getPage()
                    || data.getPageSize() != ticketSearchQuery.getPageSize()
                    || data.getTotal() < 0 || data.getRecords().size() > data.getPageSize()) {
                throw new IllegalStateException("搜索服务返回的分页结果不合法");
            }

            // 批量回表，复核当前数据和筛选条件，不再分页。
            List<Tickets> tickets =
                    loadCurrentTickets(data.getRecords(), adminTicketListDto);

            // SQL IN 不保证返回顺序，需要按照 ES 的 ID 顺序恢复。
            List<TicketResponse> responses =
                    toResponsesInSearchOrder(data.getRecords(), tickets);

            return new PageResult<>(
                    ticketResponseAttachmentEnricher.enrichAll(responses),
                    data.getTotal(),
                    adminTicketListDto.getPage(),
                    adminTicketListDto.getPageSize());
        } catch (IOException e) {
            throw new IllegalStateException("工单搜索失败", e);
        }

    }

    /** 按 ES 命中顺序转换数据库中的最新工单，跳过已删除或不再满足筛选的候选。 */
    private List<TicketResponse> toResponsesInSearchOrder(List<TicketSearchDocument> records, List<Tickets> tickets) {
        Map<Long, Tickets> ticketsById = tickets.stream()
                .collect(Collectors.toMap(Tickets::getId, ticket -> ticket));
        return records.stream()
                .map(this::searchTicketId)
                .distinct()
                .map(ticketsById::get)
                .filter(Objects::nonNull)
                .map(TicketConverter::toResponse)
                .collect(Collectors.toList());
    }

    /** 仅按当前搜索页的 ID 批量回表并复核条件；空页不查数据库，回表不再分页。 */
    private List<Tickets> loadCurrentTickets(List<TicketSearchDocument> records, AdminTicketListDto adminTicketListDto) {
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ticketIds = records.stream()
                .map(this::searchTicketId)
                .distinct()
                .collect(Collectors.toList());
        // 搜索投影只提供候选 ID；展示字段和当前筛选条件以数据库为准。
        LambdaQueryWrapper<Tickets> query = adminTicketFilters(adminTicketListDto)
                .in(Tickets::getId, ticketIds);
        return ticketMapper.selectList(query);
    }

    /** 将 ES 字符串 ID 转为数据库正整数 ID；异常投影作为服务错误处理。 */
    private Long searchTicketId(TicketSearchDocument record) {
        try {
            if (record == null || !StringUtils.hasText(record.getTicketId())) {
                throw new IllegalStateException("搜索结果缺少工单 ID");
            }
            long id = Long.parseLong(record.getTicketId());
            if (id <= 0) {
                throw new IllegalStateException("搜索结果包含非法工单 ID");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("搜索结果包含非法工单 ID", exception);
        }
    }

    /**
     * 关键词为空时按创建时间和数值 ID 倒序查询 MySQL，保留数据库分页总数。
     * @param adminTicketListDto 管理员工单列表查询参数
     * @return 管理员工单列表查询结果，空页的列表为空且保留查询总数
     */
    private PageResult<TicketResponse> queryAdminPageFromMysql(AdminTicketListDto adminTicketListDto) {
        LambdaQueryWrapper<Tickets> queryWrapper = adminTicketFilters(adminTicketListDto)
                .orderByDesc(Tickets::getCreatedAt)
                .orderByDesc(Tickets::getId);
        Page<Tickets> page = ticketMapper.selectPage(
                new Page<>(adminTicketListDto.getPage(), adminTicketListDto.getPageSize()), queryWrapper);
        List<TicketResponse> responses = page.getRecords() == null ? Collections.emptyList()
                : page.getRecords().stream().map(TicketConverter::toResponse).collect(Collectors.toList());
        return new PageResult<>(ticketResponseAttachmentEnricher.enrichAll(responses), page.getTotal(),
                adminTicketListDto.getPage(), adminTicketListDto.getPageSize());
    }

    /** MySQL 分页和 ES 回表共用筛选条件，关键词交给 ES 分词查询。 */
    private LambdaQueryWrapper<Tickets> adminTicketFilters(AdminTicketListDto adminTicketListDto) {
        LambdaQueryWrapper<Tickets> queryWrapper = new LambdaQueryWrapper<>();
        String status = normalizeStatus(adminTicketListDto.getStatus());
        if (status != null) {
            queryWrapper.eq(Tickets::getStatus, status);
        }
        // 按优先级查询
        if (adminTicketListDto.getPriority() != 0) {
            queryWrapper.eq(Tickets::getPriority, adminTicketListDto.getPriority());
        }
        // 按类别查询
        if (adminTicketListDto.getCategoryId() != null) {
            queryWrapper.eq(Tickets::getCategoryId, adminTicketListDto.getCategoryId());
        }
        // 按处理人查询
        if (adminTicketListDto.getHandlerId() != null) {
            queryWrapper.eq(Tickets::getHandlerId, adminTicketListDto.getHandlerId());
        }
        // 按创建人查询
        if (adminTicketListDto.getCreatorId() != null) {
            queryWrapper.eq(Tickets::getCreatorId, adminTicketListDto.getCreatorId());
        }
        // 按SLA状态查询
        String slaStatus = trimToNull(adminTicketListDto.getSlaStatus());
        if (slaStatus != null) {
            queryWrapper.eq(Tickets::getSlaStatus, slaStatus);
        }
        // 按时间范围查询
        if (adminTicketListDto.getStart() != null) {
            queryWrapper.ge(Tickets::getCreatedAt, adminTicketListDto.getStart());
        }
        if (adminTicketListDto.getEnd() != null) {
            queryWrapper.le(Tickets::getCreatedAt, adminTicketListDto.getEnd());
        }
        return queryWrapper;
    }

    /** 生成内部搜索请求，将可选条件规范化，并按上海时区转换创建时间边界。 */
    private static TicketSearchQuery getTicketSearchQuery(AdminTicketListDto adminTicketListDto) {
        TicketSearchQuery ticketSearchQuery = new TicketSearchQuery();
        ticketSearchQuery.setStatus(normalizeStatus(adminTicketListDto.getStatus()));
        ticketSearchQuery.setPriority(adminTicketListDto.getPriority() == 0 ? null : adminTicketListDto.getPriority());
        ticketSearchQuery.setKeyword(trimToNull(adminTicketListDto.getKeyword()));
        ticketSearchQuery.setCategoryId(idToString(adminTicketListDto.getCategoryId()));
        ticketSearchQuery.setCreatorId(idToString(adminTicketListDto.getCreatorId()));
        ticketSearchQuery.setHandlerId(idToString(adminTicketListDto.getHandlerId()));
        ticketSearchQuery.setSlaStatus(trimToNull(adminTicketListDto.getSlaStatus()));
        ticketSearchQuery.setPage(Math.toIntExact(adminTicketListDto.getPage()));
        ticketSearchQuery.setPageSize(Math.toIntExact(adminTicketListDto.getPageSize()));
        if (adminTicketListDto.getStart() != null) {
            ticketSearchQuery.setStart(adminTicketListDto.getStart().atZone(TICKET_TIME_ZONE).toOffsetDateTime());
        }
        if (adminTicketListDto.getEnd() != null) {
            ticketSearchQuery.setEnd(adminTicketListDto.getEnd().atZone(TICKET_TIME_ZONE).toOffsetDateTime());
        }
        return ticketSearchQuery;
    }

    /** 校验两条查询路径的公共参数，并在关键词搜索时限制 ES 基础分页窗口。 */
    private static void validateAdminListQuery(AdminTicketListDto query) {
        Assert.notNull(query, "查询条件不能为空");
        Assert.isTrue(query.getPage() != null && query.getPage() >= 1, "页码必须大于等于 1");
        Assert.isTrue(query.getPageSize() != null && query.getPageSize() >= 1 && query.getPageSize() <= 100,
                "每页数量必须介于 1 和 100 之间");
        Assert.isTrue(query.getPriority() >= 0 && query.getPriority() <= 4, "优先级必须介于 0 和 4 之间");
        Assert.isTrue(query.getCategoryId() == null || query.getCategoryId() > 0, "分类 ID 必须为正数");
        Assert.isTrue(query.getCreatorId() == null || query.getCreatorId() > 0, "创建人 ID 必须为正数");
        Assert.isTrue(query.getHandlerId() == null || query.getHandlerId() > 0, "处理人 ID 必须为正数");
        Assert.isTrue(query.getStart() == null || query.getEnd() == null || !query.getStart().isAfter(query.getEnd()),
                "开始时间不能晚于结束时间");
        String status = normalizeStatus(query.getStatus());
        if (status != null) {
            TicketStatusEnum.valueOf(status);
        }
        Assert.isTrue(query.getKeyword() == null || query.getKeyword().length() <= 500,
                "搜索关键字不能超过 500 个字符");
        if (StringUtils.hasText(query.getKeyword())) {
            Assert.isTrue(query.getPage() <= 10000 / query.getPageSize(),
                    "基础分页仅支持前 10000 条，请缩小筛选范围");
        } else {
            Assert.isTrue(query.getPage() - 1 <= Long.MAX_VALUE / query.getPageSize(), "分页范围过大");
        }
    }

    /** 将空白和 all 统一为不限制状态，其余编码去除首尾空白。 */
    private static String normalizeStatus(String value) {
        String status = trimToNull(value);
        return "all".equalsIgnoreCase(status) ? null : status;
    }

    /** 去除首尾空白，空值或纯空白返回 null。 */
    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** 保留可选 ID 的 null 语义，避免未指定筛选时发生空指针。 */
    private static String idToString(Long id) {
        return id == null ? null : id.toString();
    }

    /**
     * 分配工单
     * @param ticketId 工单ID
     * @param assignTicketDto 分配参数
     * @param operatorId 当前操作人ID
     * @param operatorRole 当前登录角色，由后端认证信息取得
     * @param clientIp 客户端IP
     * @return 分配后的工单信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketResponse assignTicket(
            Long ticketId,
            AssignTicketDto assignTicketDto,
            Long operatorId,
            String operatorRole,
            String clientIp) {

        // 操作人角色取自后端认证信息，服务层再次校验管理员权限。
        if (!"ADMIN".equals(operatorRole)) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        Tickets ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }

        validateAssignmentStatus(ticket);

        // 校验目标处理人存在，且为启用的处理人账号。
        Long handlerId = assignTicketDto.getHandlerId();
        UserProfile handler = getEnabledHandler(handlerId);

        String reason = assignTicketDto.getReason();
        if (reason == null || reason.trim().isEmpty()) {
            reason = "管理员手动分配";
        }

        saveTicketAssignment(ticket, handlerId, reason, operatorId, operatorRole, clientIp, LocalDateTime.now(), 0);

        // updated_at 由数据库自动维护，重新查询以返回数据库中的最新字段值。
        Tickets assignedTicket = ticketMapper.selectById(ticketId);
        if (assignedTicket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }
        TicketResponse response = TicketConverter.toResponse(assignedTicket);
        response.setHandlerName(handler.getRealName());
        return ticketResponseAttachmentEnricher.enrich(response);
    }

    /**
     * 批量分配工单
     * @param ticketIds 工单ID列表
     * @param handlerId 目标处理人ID
     * @param operatorId 当前操作人ID
     * @param operatorRole 当前登录角色，由后端认证信息取得
     * @param clientIp 客户端IP
     * @return 分配后的工单ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> assignTicketList(
            List<Long> ticketIds,
            Long handlerId,
            Long operatorId,
            String operatorRole,
            String clientIp) {

        if (!"ADMIN".equals(operatorRole)) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }
        if (ticketIds == null || ticketIds.isEmpty()
                || ticketIds.stream().anyMatch(id -> id == null || id <= 0)
                || handlerId == null || handlerId <= 0) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }

        // 与批量关闭保持一致，校验操作人存在且为启用状态。
        Result<UserProfile> userResult = userFeignClient.getById(operatorId);
        if (userResult == null || userResult.getData() == null) {
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }
        if (userResult.getData().getStatus() != 1) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_DISABLED);
        }
        // 同一批次只校验一次目标处理人。
        getEnabledHandler(handlerId);

        // 去重并保持请求顺序，一次查询后确认整批工单均存在且允许分配。
        List<Long> assignedTicketIds = ticketIds.stream().distinct().collect(Collectors.toList());
        List<Tickets> tickets = ticketMapper.selectBatchIds(assignedTicketIds);
        if (tickets == null || tickets.size() != assignedTicketIds.size()) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }
        // 构建工单ID与工单信息的映射，后续根据ID快速获取工单信息
        Map<Long, Tickets> ticketsById = tickets.stream()
                .collect(Collectors.toMap(Tickets::getId, ticket -> ticket));
        for (Long ticketId : assignedTicketIds) {
            validateAssignmentStatus(ticketsById.get(ticketId));
        }

        LocalDateTime assignedAt = LocalDateTime.now();
        int additionalLoad = 0;
        for (Long ticketId : assignedTicketIds) {
            saveTicketAssignment(ticketsById.get(ticketId), handlerId, "管理员批量分配",
                    operatorId, operatorRole, clientIp, assignedAt, additionalLoad++);
        }
        return assignedTicketIds;
    }

    /**
     * 校验工单是否允许分配，已有处理人且非待分配时必须使用转派接口。
     */
    private void validateAssignmentStatus(Tickets ticket) {
        if (ticket.getHandlerId() != null
                && !TicketStatusEnum.PENDING_ASSIGN.name().equals(ticket.getStatus())) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_NOT_ALLOWED);
        }
    }

    /**
     * 获取启用的处理人账号。
     */
    private UserProfile getEnabledHandler(Long handlerId) {
        Result<UserProfile> result = userFeignClient.getById(handlerId);
        if (result == null || result.getData() == null) {
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }
        UserProfile handler = result.getData();
        if (handler.getRole() != 1 || handler.getStatus() != 1) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_DISABLED);
        }
        return handler;
    }

    /**
     * 保存工单分配信息
     * @param ticket 工单信息
     * @param handlerId 处理人ID
     * @param reason 分配原因
     * @param operatorId 操作人ID
     * @param operatorRole 操作人角色
     * @param clientIp 客户端IP
     * @param assignedAt 分配时间
     * @param additionalLoad 额外负载
     */
    private void saveTicketAssignment(
            Tickets ticket,
            Long handlerId,
            String reason,
            Long operatorId,
            String operatorRole,
            String clientIp,
            LocalDateTime assignedAt,
            int additionalLoad) {

        Long ticketId = ticket.getId();
        ticketMapper.lockHandlerProfile(handlerId);
        String oldStatus = ticket.getStatus();
        Long oldHandlerId = ticket.getHandlerId();
        boolean isPendingAssign = TicketStatusEnum.PENDING_ASSIGN.name().equals(oldStatus);
        // 设置处理人ID与分配时间
        ticket.setHandlerId(handlerId);
        ticket.setAssignedAt(assignedAt);
        if (isPendingAssign) {
            ticket.setStatus(TicketStatusEnum.PENDING_RESPONSE.name());
        }

        // 同时核对原状态与原处理人，避免并发分配、转派或状态流转被覆盖。
        int update = ticketMapper.update(null, new LambdaUpdateWrapper<Tickets>()
                .eq(Tickets::getId, ticketId)
                .eq(Tickets::getStatus, oldStatus)
                .isNull(oldHandlerId == null, Tickets::getHandlerId)
                .eq(oldHandlerId != null, Tickets::getHandlerId, oldHandlerId)
                .set(Tickets::getHandlerId, handlerId)
                .set(Tickets::getAssignedAt, assignedAt)
                .set(Tickets::getStatus, ticket.getStatus())
                .setSql("source_version = source_version + 1"));
        if (update != 1) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 分配记录与工单、状态历史、操作日志在同一事务中保存。
        AssignmentRecord assignmentRecord = new AssignmentRecord();
        assignmentRecord.setTicketId(ticketId);
        assignmentRecord.setHandlerId(handlerId);
        assignmentRecord.setAssignedBy("MANUAL");
        assignmentScoreService.fill(ticketId, handlerId, additionalLoad, assignmentRecord);
        assignmentRecord.setCreatedAt(assignedAt);
        if (assignmentRecordMapper.insert(assignmentRecord) != 1) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 状态历史
        if (!saveTicketStatusHistory(ticket, oldStatus, operatorId, reason, "ASSIGN", assignedAt)) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 操作日志
        if (!saveTicketOperationLog(
                ticket,
                "ASSIGN",
                operatorId,
                operatorRole,
                clientIp,
                "手动分配工单至处理人ID：" + handlerId + "，原因：" + reason,
                assignedAt)) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 与工单、分配记录和审计记录处于同一事务；Outbox 写入失败时整笔派单回滚。
        ticketAssignedEventPublisher.publish(ticket, handlerId, operatorId, assignedAt, reason);
        // 工单搜索索引更新
        ticketSearchChangePublisher.publish(ticketId);

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketResponse closeTicket(Long ticketId, CloseTicketDto closeTicketDto, Long operatorId, String operatorRole, String clientIp) {
        // 操作人角色取自后端认证信息，服务层再次校验管理员权限。
        if (!"ADMIN".equals(operatorRole)) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        // 校验操作人存在且为启用状态
        Result<UserProfile> userResult = userFeignClient.getById(operatorId);
        if (userResult == null || userResult.getData() == null) {
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }
        UserProfile operator = userResult.getData();
        if (operator.getStatus() != 1){
            throw new SystemException(SystemExceptionEnum.ACCOUNT_DISABLED);
        }

        // 校验工单存在
        Tickets ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }

        // 检查工单状态是否允许关闭
        if (
                TicketStatusEnum.CLOSED.name().equals(ticket.getStatus()) ||
                TicketStatusEnum.CANCELLED.name().equals(ticket.getStatus())
        ){
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_NOT_ALLOWED);
        }

        String oldStatus = ticket.getStatus();

        ticket.setStatus(TicketStatusEnum.CLOSED.name());
        ticket.setClosedAt(LocalDateTime.now());

        int update = ticketMapper.update(null, new LambdaUpdateWrapper<Tickets>()
                        .eq(Tickets::getId, ticketId)
                        .eq(Tickets::getStatus, oldStatus)
                        .set(Tickets::getStatus, ticket.getStatus())
                        .set(Tickets::getClosedAt, ticket.getClosedAt())
                        .setSql("source_version = source_version + 1"));
        if (update != 1) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        if (!saveTicketStatusHistory(
                ticket,
                oldStatus,
                operatorId,
                closeTicketDto.getReason(),
                "CLOSE",
                ticket.getClosedAt())) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        if (!saveTicketOperationLog(
                ticket,
                "CLOSE",
                operatorId,
                operatorRole,
                clientIp,
                "关闭工单，原因：" + closeTicketDto.getReason(),
                ticket.getClosedAt())) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        ticketSearchChangePublisher.publish(ticketId);
        return ticketResponseAttachmentEnricher.enrich(TicketConverter.toResponse(ticket));
    }

    /**
     * 批量关闭工单
     * @param closedTickets 工单ID列表
     * @param reason 关闭原因
     * @param operatorId 操作人ID
     * @param operatorRole 操作人角色
     * @param clientIp 客户端IP
     * @return 关闭的工单ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> closeTicketList(List<Long> closedTickets, String reason, Long operatorId, String operatorRole, String clientIp) {
        if (!"ADMIN".equals(operatorRole)) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        // 校验操作人存在且为启用状态
        Result<UserProfile> userResult = userFeignClient.getById(operatorId);
        if (userResult == null || userResult.getData() == null) {
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }
        if (userResult.getData().getStatus() != 1) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_DISABLED);
        }

        if (closedTickets == null || closedTickets.isEmpty()) {
            return Collections.emptyList();
        }
        if (closedTickets.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }

        // 去重后一次查询，避免重复关闭，并在写入前确认所有工单存在。
        List<Long> ticketIds = closedTickets.stream().distinct().collect(Collectors.toList());
        // 一次查询所有工单，避免多次查询数据库
        List<Tickets> tickets = ticketMapper.selectBatchIds(ticketIds);
        if (tickets == null || tickets.size() != ticketIds.size()) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }
        Map<Long, Tickets> ticketsById = tickets.stream()
                .collect(Collectors.toMap(Tickets::getId, ticket -> ticket));

        // 构建关闭原因，如果未提供原因则使用默认值
        String closeReason = reason == null || reason.trim().isEmpty() ? "管理员批量关闭" : reason;
        LocalDateTime closedAt = LocalDateTime.now();
        List<Long> closedTicketIds = new ArrayList<>();
        for (Long ticketId : ticketIds) {
            Tickets ticket = ticketsById.get(ticketId);
            String oldStatus = ticket.getStatus();
            // 终态工单无需再次关闭，也不重复生成审计记录。
            if (TicketStatusEnum.CLOSED.name().equals(oldStatus)
                    || TicketStatusEnum.CANCELLED.name().equals(oldStatus)) {
                continue;
            }

            // 核对查询时的状态，避免覆盖并发发生的状态变更。
            int update = ticketMapper.update(null, new LambdaUpdateWrapper<Tickets>()
                    .eq(Tickets::getId, ticketId)
                    .eq(Tickets::getStatus, oldStatus)
                    .set(Tickets::getStatus, TicketStatusEnum.CLOSED.name())
                    .set(Tickets::getClosedAt, closedAt)
                    .setSql("source_version = source_version + 1"));
            if (update != 1) {
                throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
            }
            ticket.setStatus(TicketStatusEnum.CLOSED.name());
            ticket.setClosedAt(closedAt);

            // 保存状态历史
            if (!saveTicketStatusHistory(ticket, oldStatus, operatorId, closeReason, "CLOSE", closedAt)) {
                throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
            }
            // 保存操作日志
            if (!saveTicketOperationLog(ticket, "CLOSE", operatorId, operatorRole, clientIp,
                    "关闭工单，原因：" + closeReason, closedAt)) {
                throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
            }
            ticketSearchChangePublisher.publish(ticketId);
            closedTicketIds.add(ticketId);
        }
        return closedTicketIds;
    }

    /**
     * 保存工单状态历史
     * @param ticket 变更后的工单
     * @param oldStatus 旧状态
     * @param operatorId 操作人ID
     * @param remark 备注
     * @param event 触发事件
     * @param createdAt 事件发生时间
     * @return 是否保存成功
     */
    private boolean saveTicketStatusHistory(
            Tickets ticket,
            String oldStatus,
            Long operatorId,
            String remark,
            String event,
            LocalDateTime createdAt) {

        TicketStatusHistory history = new TicketStatusHistory();
        history.setTicketId(ticket.getId());
        history.setFromStatus(oldStatus);
        history.setToStatus(ticket.getStatus());
        history.setEvent(event);
        history.setOperatorId(operatorId);
        history.setRemark(remark);
        history.setCreatedAt(createdAt);
        return ticketStatusHistoryMapper.insert(history) == 1;
    }

    /**
     * 保存工单操作日志
     * @param ticket 工单
     * @param action 操作类型
     * @param operatorId 操作人ID
     * @param operatorRole 操作人角色
     * @param clientIp 客户端IP
     * @param content 操作内容
     * @param createdAt 事件发生时间
     * @return 是否保存成功
     */
    private boolean saveTicketOperationLog(
            Tickets ticket,
            String action,
            Long operatorId,
            String operatorRole,
            String clientIp,
            String content,
            LocalDateTime createdAt) {

        TicketOperationLog log = new TicketOperationLog();
        log.setTicketId(ticket.getId());
        log.setAction(action);
        log.setOperatorId(operatorId);
        log.setOperatorRole(operatorRole);
        log.setIpAddress(clientIp);
        log.setContent(content);
        log.setCreatedAt(createdAt);
        return ticketOperationLogMapper.insert(log) == 1;
    }
}

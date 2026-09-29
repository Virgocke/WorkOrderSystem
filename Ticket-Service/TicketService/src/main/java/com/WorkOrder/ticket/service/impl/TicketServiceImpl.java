package com.WorkOrder.ticket.service.impl;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.file.mapper.AttachmentMapper;
import com.WorkOrder.file.service.AttachmentService;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.model.ticket.TicketResponse;
import com.WorkOrder.model.user.UserProfile;
import com.WorkOrder.ticket.converter.TicketConverter;
import com.WorkOrder.ticket.dto.*;
import com.WorkOrder.ticket.enums.TicketStatusEnum;
import com.WorkOrder.ticket.mapper.TicketCategoryMapper;
import com.WorkOrder.ticket.mapper.TicketMapper;
import com.WorkOrder.ticket.mapper.TicketOperationLogMapper;
import com.WorkOrder.ticket.mapper.TicketStatusHistoryMapper;
import com.WorkOrder.ticket.messaging.TicketCreatedEventPublisher;
import com.WorkOrder.ticket.messaging.TicketRemindedEventPublisher;
import com.WorkOrder.ticket.messaging.TicketRepliedEventPublisher;
import com.WorkOrder.ticket.messaging.TicketTerminalEventPublisher;
import com.WorkOrder.ticket.model.*;
import com.WorkOrder.ticket.service.TicketService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Virgor
 * @date 2026年09月14日 04:08
 * @description 工单服务实现类
 */
@RequiredArgsConstructor
@Service
public class TicketServiceImpl extends ServiceImpl<TicketMapper, Tickets> implements TicketService {

    private static final List<String> SLA_BOARD_STATUSES = Collections.unmodifiableList(Arrays.asList(
            TicketStatusEnum.PENDING_RESPONSE.name(),
            TicketStatusEnum.PROCESSING.name(),
            TicketStatusEnum.RESOLVED.name()));
    /**
     * SLA状态列表
     */
    private static final List<String> SLA_STATUSES = Collections.unmodifiableList(Arrays.asList(
            "NORMAL", "NEAR_TIMEOUT", "TIMEOUT", "ESCALATED"));

    /** 仍处于处理链路、允许用户催办的工单状态。 */
    private static final Set<String> REMINDABLE_STATUSES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    TicketStatusEnum.PENDING_ASSIGN.name(),
                    TicketStatusEnum.PENDING_RESPONSE.name(),
                    TicketStatusEnum.PROCESSING.name())));

    /**
     * SLA板排序
     */
    private static final String SLA_BOARD_ORDER =
            "ORDER BY (CASE WHEN status = 'PENDING_RESPONSE' THEN response_deadline "
                    + "ELSE resolution_deadline END) IS NULL ASC, "
                    + "CASE WHEN status = 'PENDING_RESPONSE' THEN response_deadline "
                    + "ELSE resolution_deadline END ASC, id ASC";

    private final TicketMapper ticketMapper;
    private final TicketCategoryMapper categoryMapper;
    private final TicketStatusHistoryMapper ticketStatusHistoryMapper;
    private final TicketOperationLogMapper ticketOperationLogMapper;
    private final AttachmentMapper attachmentMapper;
    private final AttachmentService attachmentService;
    /** 工单创建事件的事务内发布器。 */
    private final TicketCreatedEventPublisher ticketCreatedEventPublisher;
    private final TicketRepliedEventPublisher ticketRepliedEventPublisher;
    private final TicketRemindedEventPublisher ticketRemindedEventPublisher;
    /** 关闭和撤销工单事件的事务内发布器。 */
    private final TicketTerminalEventPublisher ticketTerminalEventPublisher;


    /**
     * 创建工单
     * @param creatorId 创建者ID
     * @param creatorName 创建者名称
     * @param createTicketDto 创建工单DTO
     * @return 工单响应对象
     */
    @Transactional
    @Override
    public TicketResponse createTicket(Long creatorId, String creatorName, CreateTicketDto createTicketDto) {
        TicketResponse ticketResponse = new TicketResponse(); // 创建工单响应对象
        TicketCategory ticketCategory = categoryMapper.selectById(createTicketDto.getCategoryId()); // 根据类别ID获取类别信息
        LocalDateTime now = LocalDateTime.now(); // 获取当前时间

        if (createTicketDto.getPriority() < 1 || createTicketDto.getPriority() > 4) {
            int defaultPriority = ticketCategory.getDefaultPriority();
            createTicketDto.setPriority(defaultPriority);
        }

        Tickets ticket = new Tickets();
        BeanUtils.copyProperties(createTicketDto, ticket);
        ticket.setCreatorId(creatorId); // 设置创建者ID

        //todo 生成工单编号，现在暂时使用随机数模拟
        String ticketNo = String.valueOf((int) (Math.random() * 10000000));
        ticket.setTicketNo(ticketNo); // 设置工单编号

        ticketResponse.setCategoryName(ticketCategory.getName()); // 设置工单类别名称
        String creatorDisplayName = ticketMapper.selectDisplayNameByUserId(creatorId);
        ticketResponse.setCreatorName(creatorDisplayName == null ? creatorName : creatorDisplayName);
        ticketResponse.setHandlerId(null); // 设置处理者ID为null
        ticketResponse.setHandlerName(null); // 设置处理者名称为null
        ticketResponse.setAssignedAt(null); // 设置分配时间为空

        ticket.setStatus(TicketStatusEnum.PENDING_ASSIGN.name()); // 设置工单状态为待分配
        ticket.setResponseDeadline(
                now.plusMinutes(ticketCategory.getDefaultResponseSla())
        ); // 设置响应截止时间

        ticket.setResolutionDeadline(
                now.plusMinutes(ticketCategory.getDefaultResolutionSla())
        ); // 设置解决截止时间

        ticket.setFirstResponseAt(null); // 设置首次响应时间为null
        ticket.setResolvedAt(null); // 设置解决时间为null
        ticket.setClosedAt(null); // 设置关闭时间为null
        ticket.setSlaStatus("NORMAL"); // 设置SLA状态为正常
        ticket.setEscalatedLevel(0); // 设置升级级别为0
        ticket.setRemindCount(0); // 设置催办次数为0
        ticket.setSource("WEB"); // 设置来源为WEB

        int insert = ticketMapper.insert(ticket);
        if (insert < 1){
            throw new SystemException(SystemExceptionEnum.TICKET_CREATE_FAILED);
        }

        attachmentService.bindToTicket(
                creatorId,
                ticket.getId(),
                createTicketDto.getAttachmentIds()
        );

        /**
         * 创建工单状态历史记录
         */
        TicketStatusHistory history = new TicketStatusHistory();
        history.setTicketId(ticket.getId());
        history.setFromStatus(null);
        history.setToStatus(TicketStatusEnum.PENDING_ASSIGN.name());
        history.setEvent("CREATE");
        history.setOperatorId(creatorId);
        history.setRemark("用户创建工单");
        history.setCreatedAt(now);
        if (ticketStatusHistoryMapper.insert(history) != 1) {
            throw new SystemException(SystemExceptionEnum.TICKET_CREATE_FAILED);
        }

        // 工单、附件绑定、状态历史和创建事件在同一事务中提交。
        ticketCreatedEventPublisher.publish(ticket, now);


        Tickets newTicket = ticketMapper.selectById(ticket.getId());
        if (newTicket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_CREATE_FAILED);
        }

        return withAttachmentUrls(
                TicketConverter.toResponse(newTicket, ticketResponse),
                newTicket.getId()
        );
    }

    /**
     * 根据用户ID和状态查询工单列表
     * @param userId 用户ID
     * @param myTicketPageDto 工单分页查询DTO
     * @return 工单响应对象列表
     */
    @Override
    public List<TicketResponse> myTickets(Long userId, MyTicketPageDto myTicketPageDto) {
        // 根据用户ID和状态查询工单列表
        List<Tickets> myTickets = null;
        if (myTicketPageDto.getStatus().equals("all") || myTicketPageDto.getStatus().isEmpty()) {
            myTickets = ticketMapper.selectList(
                    new LambdaQueryWrapper<Tickets>()
                            .eq(Tickets::getCreatorId, userId)
                            .orderByDesc(Tickets::getUpdatedAt)
                            .orderByDesc(Tickets::getId)
                            .last("limit " + myTicketPageDto.getPageSize()));
        } else {
            myTickets = ticketMapper.selectList(
                    new LambdaQueryWrapper<Tickets>()
                            .eq(Tickets::getCreatorId, userId)
                            .eq(Tickets::getStatus, myTicketPageDto.getStatus())
                            .orderByDesc(Tickets::getUpdatedAt)
                            .orderByDesc(Tickets::getId)
                            .last("limit " + myTicketPageDto.getPageSize()));
        }
        if (myTickets == null || myTickets.isEmpty()){
            return Collections.emptyList();
        }

        // 构建工单响应对象列表
        List<TicketResponse> ticketResponses = myTickets.stream()
                .map(this::toEnrichedResponse)
                .collect(Collectors.toList());

        return ticketResponses;
    }

    /**
     * 获取工单历史统计信息
     * @param userId 用户ID
     * @return 工单历史统计信息
     */
    @Override
    public TicketHistoryStatisticsDto getTicketHistoryStatistics(Long userId) {
        TicketHistoryStatisticsDto statistics =
                ticketMapper.selectHistoryStatistics(userId);

        List<MonthlyCountDto> byMonth =
                ticketMapper.selectMonthlyStatistics(userId);

        statistics.setByMonth(byMonth);
        return statistics;
    }

    /**
     * 根据工单ID获取工单信息
     * @param ticketId 工单ID
      * @return 工单响应对象
     */
    @Override
    public TicketResponse getTicketInfo(Long ticketId) {
        Tickets ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }
        return toEnrichedResponse(ticket);
    }

    /**
     * 补齐不在 tickets 表中的展示字段，保证详情和列表刷新后仍能显示分类及人员名称。
     */
    private TicketResponse toEnrichedResponse(Tickets ticket) {
        TicketCategory category = categoryMapper.selectById(ticket.getCategoryId());
        String categoryName = category == null ? null : category.getName();
        String creatorName = ticketMapper.selectDisplayNameByUserId(ticket.getCreatorId());
        String handlerName = ticket.getHandlerId() == null
                ? null
                : ticketMapper.selectDisplayNameByUserId(ticket.getHandlerId());
        return withAttachmentUrls(
                TicketConverter.toResponse(ticket, categoryName, creatorName, handlerName),
                ticket.getId()
        );
    }

    /**
     * 回复工单
     * @param ticketId 工单ID
     * @param userId 用户ID
     * @param operatorRole 当前登录角色，由后端认证信息取得
     * @param ticketReplyDto 回复工单的DTO
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean ticketReplyInfo(Long ticketId, Long userId, String operatorRole, TicketReplyDto ticketReplyDto) {
        Tickets ticket = ticketMapper.selectById(ticketId);
        if (ticket == null){
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }

        List<Long> attachmentIds = Optional.ofNullable(ticketReplyDto.getAttachmentIds())
                .orElse(Collections.emptyList());
        if (attachmentIds.size() > 6) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }

        // 操作类型
        String action;
        // 接收者ID
        Long receiverId;
        // 工单中的业务身份决定回复类型，登录角色独立保存到日志。
        if (Objects.equals(userId, ticket.getCreatorId())) {
            action = "USER_REPLY";
            receiverId = ticket.getHandlerId();
        } else if (Objects.equals(userId, ticket.getHandlerId())) {
            action = "HANDLER_REPLY";
            receiverId = ticket.getCreatorId();
        } else if ("ADMIN".equals(operatorRole)) {
            // 管理员属于客服侧回复
            action = "HANDLER_REPLY";
            receiverId = ticket.getCreatorId();
        } else {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        // 创建工单操作日志
        LocalDateTime repliedAt = LocalDateTime.now();
        TicketOperationLog log = new TicketOperationLog();
        log.setTicketId(ticketId);
        log.setAction(action);
        log.setOperatorId(userId);
        log.setOperatorRole(operatorRole);
        log.setCreatedAt(repliedAt);

        String content = "";
        if (ticketReplyDto.getContent() != null){
            content = content + ticketReplyDto.getContent();
            log.setContent(content);
        }


        // 插入工单操作日志
        int insert = ticketOperationLogMapper.insert(log);

        if (insert < 1){
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }

        attachmentService.bindToOperationLog(
                userId,
                ticketId,
                log.getId(),
                attachmentIds
        );


        // 与回复日志和附件绑定处于同一事务；Outbox 写入失败时整笔回复回滚。
        ticketRepliedEventPublisher.publish(
                ticket,
                log,
                receiverId,
                repliedAt,
                attachmentIds.size()
        );

        return true;
    }

    /**
     * 催办工单
     * @param ticketId 工单ID
     * @param userId 用户ID
      * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean ticketExpedite(Long ticketId, Long userId, String clientIp) {
        Tickets ticket = ticketMapper.selectById(ticketId);
        validateReminderRequest(ticket, userId);

        if (ticketMapper.incrementRemindCount(ticketId, userId) != 1) {
            // 状态、权限或次数可能在并发请求中变化，重新查询以返回准确错误。
            Tickets latestTicket = ticketMapper.selectById(ticketId);
            validateReminderRequest(latestTicket, userId);
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // UPDATE 持有该工单行锁直到事务结束；此处读取的是本次原子累加后的稳定快照。
        Tickets remindedTicket = ticketMapper.selectById(ticketId);
        if (remindedTicket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }
        LocalDateTime remindedAt = LocalDateTime.now();

        // 创建工单操作日志
        TicketOperationLog log = new TicketOperationLog();
        log.setTicketId(ticketId);
        log.setAction("REMIND");
        log.setOperatorId(userId);
        log.setOperatorRole("USER");
        log.setContent("用户催办工单");
        log.setIpAddress(clientIp);
        log.setCreatedAt(remindedAt);

        // 插入工单操作日志
        int insert = ticketOperationLogMapper.insert(log);
        if (insert < 1){
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }

        LinkedHashSet<Long> receiverIds = new LinkedHashSet<>();
        if (remindedTicket.getHandlerId() != null
                && !Objects.equals(remindedTicket.getHandlerId(), userId)) {
            receiverIds.add(remindedTicket.getHandlerId());
        }
        List<Long> activeAdminIds = ticketMapper.selectActiveAdminIds();
        if (activeAdminIds != null) {
            activeAdminIds.stream()
                    .filter(Objects::nonNull)
                    .filter(receiverId -> !Objects.equals(receiverId, userId))
                    .forEach(receiverIds::add);
        }

        // 与次数更新及操作日志处于同一事务；Outbox 写入失败时整笔催办回滚。
        ticketRemindedEventPublisher.publish(
                remindedTicket,
                log,
                remindedTicket.getRemindCount(),
                remindedAt,
                receiverIds
        );

        // 催办不修改工单状态、升级级别或 SLA 状态。
        return true;
    }

    /**
     * 校验催办权限、业务状态和次数上限。
     * 待用户确认、已关闭和已撤销工单均不允许催办。
     *
     * @param ticket 当前工单快照
     * @param userId 催办用户 ID
     */
    private void validateReminderRequest(Tickets ticket, Long userId) {
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }
        if (!Objects.equals(ticket.getCreatorId(), userId)) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }
        if (!REMINDABLE_STATUSES.contains(ticket.getStatus())) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_NOT_ALLOWED);
        }
        if (ticket.getRemindCount() >= 3) {
            throw new SystemException(SystemExceptionEnum.TICKET_ALREADY_ESCALATED);
        }
    }

    /**
     * 撤销工单
     * @param ticketId 工单ID
     * @param userId 用户ID
     * @return 工单响应对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketResponse cancelTicket(Long ticketId, Long userId) {
        Tickets ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }
        String ticketStatus = ticket.getStatus();
        if (!TicketStatusEnum.PENDING_ASSIGN.name().equals(ticketStatus)
                && !TicketStatusEnum.PENDING_RESPONSE.name().equals(ticketStatus)
                && !TicketStatusEnum.PROCESSING.name().equals(ticketStatus)) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_NOT_ALLOWED);
        }
        LocalDateTime cancelledAt = LocalDateTime.now();
        int update = ticketMapper.update(null, new LambdaUpdateWrapper<Tickets>()
                .eq(Tickets::getId, ticketId)
                .eq(Tickets::getStatus, ticketStatus)
                .set(Tickets::getStatus, TicketStatusEnum.CANCELLED.name()));
        if (update != 1) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }
        ticket.setStatus(TicketStatusEnum.CANCELLED.name());

        TicketStatusHistory history = new TicketStatusHistory();
        history.setTicketId(ticketId);
        history.setFromStatus(ticketStatus);
        history.setToStatus(TicketStatusEnum.CANCELLED.name());
        history.setEvent("CANCEL");
        history.setOperatorId(userId);
        history.setRemark("撤销工单");
        history.setCreatedAt(cancelledAt);
        if (ticketStatusHistoryMapper.insert(history) != 1) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 工单状态、历史和 Outbox 同事务提交，发布失败时整体回滚。
        ticketTerminalEventPublisher.publishCancelled(ticket, ticketStatus, userId, cancelledAt);

        return withAttachmentUrls(TicketConverter.toResponse(ticket), ticket.getId());
    }

    /**
     * 确认工单
     * @param ticketId 工单ID
     * @param userId 用户ID
     * @return 工单响应对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketResponse confirmTicket(Long ticketId, Long userId) {
        Tickets ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }
        if (!TicketStatusEnum.RESOLVED.name().equals(ticket.getStatus())) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_NOT_ALLOWED);
        }
        LocalDateTime closedAt = LocalDateTime.now();
        int update = ticketMapper.update(null, new LambdaUpdateWrapper<Tickets>()
                .eq(Tickets::getId, ticketId)
                .eq(Tickets::getStatus, TicketStatusEnum.RESOLVED.name())
                .set(Tickets::getStatus, TicketStatusEnum.CLOSED.name())
                .set(Tickets::getClosedAt, closedAt));
        if (update != 1) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }
        ticket.setStatus(TicketStatusEnum.CLOSED.name());
        ticket.setClosedAt(closedAt);

        TicketStatusHistory history = new TicketStatusHistory();
        history.setTicketId(ticketId);
        history.setFromStatus(TicketStatusEnum.RESOLVED.name());
        history.setToStatus(TicketStatusEnum.CLOSED.name());
        history.setEvent("CONFIRM_RESOLUTION");
        history.setOperatorId(userId);
        history.setRemark("确认解决并关闭工单");
        history.setCreatedAt(closedAt);
        if (ticketStatusHistoryMapper.insert(history) != 1) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }
        // 工单状态、历史和 Outbox 同事务提交，发布失败时整体回滚。
        ticketTerminalEventPublisher.publishClosed(ticket, userId);
        return withAttachmentUrls(TicketConverter.toResponse(ticket), ticket.getId());
    }

    /**
     * 获取当前操作人有权查看的实时 SLA 工单。
     * @param operatorId 当前操作人ID
     * @param operatorRole 当前操作人角色
     * @param page 页码
     * @param pageSize 每页条数
     * @param slaStatus SLA状态
     * @param status 工单状态
     * @return 工单分页结果
     */
    @Override
    public PageResult<TicketResponse> getTicketsBySlaStatus(
            Long operatorId,
            String operatorRole,
            Long page,
            Long pageSize,
            String slaStatus,
            String status) {
        // 验证请求参数
        validateSlaBoardRequest(operatorId, operatorRole, page, pageSize);
        // 规范化过滤值
        String normalizedSlaStatus = normalizeFilter(slaStatus);
        // 验证过滤值
        String normalizedStatus = normalizeFilter(status);
        if (normalizedSlaStatus != null && !SLA_STATUSES.contains(normalizedSlaStatus)) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        if (normalizedStatus != null && !SLA_BOARD_STATUSES.contains(normalizedStatus)) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }

        LambdaQueryWrapper<Tickets> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Tickets::getStatus, SLA_BOARD_STATUSES);
        if (normalizedSlaStatus != null) {
            queryWrapper.eq(Tickets::getSlaStatus, normalizedSlaStatus);
        }
        if (normalizedStatus != null) {
            queryWrapper.eq(Tickets::getStatus, normalizedStatus);
        }
        if ("HANDLER".equals(operatorRole)) {
            queryWrapper.eq(Tickets::getHandlerId, operatorId);
        }
        // 添加排序
        queryWrapper.last(SLA_BOARD_ORDER);

        Page<Tickets> ticketPage = ticketMapper.selectPage(new Page<>(page, pageSize), queryWrapper);
        List<TicketResponse> records = toEnrichedResponses(ticketPage.getRecords());
        return new PageResult<>(records, ticketPage.getTotal(), page, pageSize);
    }

    /**
     * 验证 SLA 板请求参数
     * @param operatorId 当前操作人ID
     * @param operatorRole 当前操作人角色
     * @param page 页码
     * @param pageSize 每页条数
     */
    private void validateSlaBoardRequest(
            Long operatorId, String operatorRole, Long page, Long pageSize) {
        if (operatorId == null || operatorId <= 0) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_OFFLINE);
        }
        if (!"ADMIN".equals(operatorRole) && !"HANDLER".equals(operatorRole)) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }
        if (page == null || pageSize == null || page < 1 || pageSize < 1) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        try {
            Math.multiplyExact(page - 1, pageSize);
        } catch (ArithmeticException exception) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
    }

    /**
     * 规范化过滤值
     * @param value 过滤值
     * @return 规范化后的过滤值
     */
    private String normalizeFilter(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    /** 分别批量查询分类和用户名称，使每页最多只增加两次查询。 */
    private List<TicketResponse> toEnrichedResponses(List<Tickets> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> categoryIds = tickets.stream()
                .map(Tickets::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> categoryNames = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            List<TicketCategory> categories = categoryMapper.selectBatchIds(categoryIds);
            if (categories != null) {
                for (TicketCategory category : categories) {
                    categoryNames.put(category.getId(), category.getName());
                }
            }
        }

        Set<Long> userIds = new LinkedHashSet<>();
        for (Tickets ticket : tickets) {
            if (ticket.getCreatorId() != null) {
                userIds.add(ticket.getCreatorId());
            }
            if (ticket.getHandlerId() != null) {
                userIds.add(ticket.getHandlerId());
            }
        }
        Map<Long, String> userNames = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<UserProfile> users = ticketMapper.selectDisplayNamesByUserIds(userIds);
            if (users != null) {
                for (UserProfile user : users) {
                    userNames.put(user.getId(), displayName(user));
                }
            }
        }

        Map<Long, List<String>> attachmentUrlsByTicketId =
                attachmentService.getAttachmentUrlsByTicketIds(
                        tickets.stream().map(Tickets::getId).collect(Collectors.toList()));

        return tickets.stream()
                .map(ticket -> {
                    TicketResponse response = TicketConverter.toResponse(
                            ticket,
                            categoryNames.get(ticket.getCategoryId()),
                            userNames.get(ticket.getCreatorId()),
                            userNames.get(ticket.getHandlerId()));
                    response.setAttachmentUrls(attachmentUrlsByTicketId.getOrDefault(
                            ticket.getId(), Collections.emptyList()));
                    return response;
                })
                .collect(Collectors.toList());
    }

    private TicketResponse withAttachmentUrls(TicketResponse response, Long ticketId) {
        Map<Long, List<String>> urlsByTicketId = attachmentService
                .getAttachmentUrlsByTicketIds(Collections.singleton(ticketId));
        response.setAttachmentUrls(urlsByTicketId.getOrDefault(ticketId, Collections.emptyList()));
        return response;
    }

    private String displayName(UserProfile user) {
        if (user.getRealName() != null && !user.getRealName().trim().isEmpty()) {
            return user.getRealName().trim();
        }
        return user.getUsername();
    }
}

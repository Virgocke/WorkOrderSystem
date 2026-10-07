package com.WorkOrder.search.service;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.search.config.TicketSearchSyncProperties;
import com.WorkOrder.search.mapper.TicketSearchDiagnosticsMapper;
import com.WorkOrder.search.messaging.TicketSearchChangedListener;
import com.WorkOrder.search.model.admin.TicketSearchConsumerStatus;
import com.WorkOrder.search.model.admin.TicketSearchDiagnosticState;
import com.WorkOrder.search.model.admin.TicketSearchOutboxStatus;
import com.WorkOrder.search.model.admin.TicketSearchQueueStatus;
import com.WorkOrder.search.model.admin.TicketSearchRuntimeStatus;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.impl.MQClientAPIImpl;
import org.apache.rocketmq.client.impl.consumer.DefaultMQPushConsumerImpl;
import org.apache.rocketmq.client.impl.consumer.ProcessQueue;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.ServiceState;
import org.apache.rocketmq.common.constant.PermName;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.protocol.header.QueryConsumerOffsetRequestHeader;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.route.BrokerData;
import org.apache.rocketmq.common.protocol.route.QueueData;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 通过现有消费者只读采集 Broker 位置，发布时拒绝未知或仍有活跃积压的观测。
 */
@Service
public class TicketSearchRuntimeStatusService {
    private static final long RPC_TIMEOUT_MS = 3000L;

    private final TicketSearchDiagnosticsMapper diagnosticsMapper;
    private final ObjectProvider<DefaultRocketMQListenerContainer> containers;
    private final String topic;
    private final String consumerGroup;
    private final int staleSeconds;

    /**
     * 保存诊断依赖；不创建新 MQ 客户端、不在构造时连接数据库或 Broker。
     *
     * @param diagnosticsMapper 主库 Outbox 积压与陈旧发送记录的聚合查询 Mapper
     * @param containers 当前进程已有 RocketMQ 监听容器的可选提供器
     * @param properties 工单搜索同步配置属性
     * @param topic 工单搜索变更消息的主 Topic 名称
     * @param staleSeconds 判定发送中 Outbox 记录陈旧的阈值，单位为秒
     */
    public TicketSearchRuntimeStatusService(TicketSearchDiagnosticsMapper diagnosticsMapper,
                                           ObjectProvider<DefaultRocketMQListenerContainer> containers,
                                           TicketSearchSyncProperties properties,
                                           @Value("${work-order.messaging.ticket-search-topic:wo-ticket-search-event}")
                                           String topic,
                                           @Value("${work-order.messaging.outbox.sending-timeout-seconds:120}")
                                           int staleSeconds) {
        Assert.notNull(diagnosticsMapper, "工单搜索诊断查询不能为空");
        Assert.notNull(containers, "工单搜索消费容器提供者不能为空");
        Assert.notNull(properties, "工单搜索同步配置不能为空");
        Assert.hasText(topic, "工单搜索 Topic 不能为空");
        Assert.hasText(properties.getConsumerGroup(), "工单搜索消费组不能为空");
        Assert.isTrue(staleSeconds > 0, "工单搜索发送锁诊断阈值必须为正数");
        this.diagnosticsMapper = diagnosticsMapper;
        this.containers = containers;
        this.topic = topic;
        this.consumerGroup = properties.getConsumerGroup();
        this.staleSeconds = staleSeconds;
    }

    /**
     * 新采集 DB 和 MQ 数据；异常明确标为 UNKNOWN，采集不占用调用方数据库事务。
     *
     * @return 本次独立采集的共享控制、Outbox 与消费队列运行事实
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TicketSearchRuntimeStatus inspect() {
        TicketSearchRuntimeStatus status = new TicketSearchRuntimeStatus();
        status.setTopic(topic);
        status.setConsumerGroup(consumerGroup);
        status.setOutbox(inspectOutbox());
        status.setConsumer(inspectConsumer());
        status.setCollectedAt(Instant.now());
        return status;
    }

    /**
     * 只接受本机消费者运行、全部队列 Broker offset 已取得、主 Topic 和活跃 Outbox 均无积压。
     * 历史 DEAD 保留展示，由完整对账提供当前状态补偿，不要求删除原失败记录。
     * 调用方应在发布预约事务之外调用，避免网络观测期间持有控制记录锁。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void requirePublishable() {
        TicketSearchRuntimeStatus status = inspect();
        TicketSearchOutboxStatus outbox = status.getOutbox();
        TicketSearchConsumerStatus consumer = status.getConsumer();
        // 发布只接受明确的“无积压”；观测失败或数量未知不能当作 0 放行。
        if (outbox.getState() != TicketSearchDiagnosticState.AVAILABLE
                || consumer.getState() != TicketSearchDiagnosticState.AVAILABLE
                || !Boolean.TRUE.equals(consumer.getLocalRunning())
                || outbox.getActiveCount() == null || outbox.getActiveCount() > 0
                || outbox.getUnknownStatusCount() == null || outbox.getUnknownStatusCount() > 0
                || consumer.getGroupBacklog() == null || consumer.getGroupBacklog() > 0) {
            throw new SystemException(SystemExceptionEnum.TICKET_SEARCH_JOB_CONFLICT);
        }
    }

    /**
     * 聚合查询返回异常或不完整计数时，数量保持未知。
     *
     * @return 工单搜索Outbox状态
     */
    private TicketSearchOutboxStatus inspectOutbox() {
        try {
            TicketSearchOutboxStatus status = diagnosticsMapper.selectOutboxStatus(topic, staleSeconds);
            // 任一计数缺失都说明本次观测不完整，保留 UNKNOWN，避免部分结果掩盖待发送消息。
            if (status == null || !nonnegative(status.getNewCount()) || !nonnegative(status.getRetryCount())
                    || !nonnegative(status.getSendingCount()) || !nonnegative(status.getStaleSendingCount())
                    || !nonnegative(status.getDeadCount()) || !nonnegative(status.getUnknownStatusCount())
                    || status.getCollectedAt() == null) {
                return unknownOutbox("OUTBOX_STATISTICS_INCOMPLETE");
            }
            status.getActiveCount();
            status.setState(TicketSearchDiagnosticState.AVAILABLE);
            return status;
        } catch (RuntimeException exception) {
            return unknownOutbox("OUTBOX_QUERY_FAILED:" + exception.getClass().getSimpleName());
        }
    }

    /**
     * 使用现有已运行容器及客户端，仅直接查询 Broker，不改 OffsetStore 的内存位置。
     *
     * @return 工单搜索消费者状态
     */
    private TicketSearchConsumerStatus inspectConsumer() {
        TicketSearchConsumerStatus status = new TicketSearchConsumerStatus();
        status.setState(TicketSearchDiagnosticState.UNKNOWN);
        try {
            // 精确定位搜索变更消费者，不能用同一服务中其他 Topic 的运行状态替代。
            List<DefaultRocketMQListenerContainer> matching = containers.orderedStream()
                    .filter(container -> topic.equals(container.getTopic())
                            && consumerGroup.equals(container.getConsumerGroup())
                            && "CHANGED".equals(container.getSelectorExpression())
                            && container.getRocketMQListener() instanceof TicketSearchChangedListener)
                    .collect(Collectors.toList());
            if (matching.size() != 1) {
                status.setLocalRunning(Boolean.FALSE);
                status.setError(matching.isEmpty() ? "LOCAL_SEARCH_CONSUMER_MISSING" : "LOCAL_SEARCH_CONSUMER_AMBIGUOUS");
                return status;
            }
            DefaultRocketMQListenerContainer container = matching.get(0);
            DefaultMQPushConsumer consumer = container.getConsumer();
            DefaultMQPushConsumerImpl implementation = consumer == null ? null : consumer.getDefaultMQPushConsumerImpl();
            boolean running = container.isRunning() && implementation != null
                    && implementation.getServiceState() == ServiceState.RUNNING && !implementation.isPause();
            status.setLocalRunning(running);
            if (!running || !consumerGroup.equals(consumer.getConsumerGroup())) {
                status.setError("LOCAL_SEARCH_CONSUMER_NOT_RUNNING_OR_GROUP_MISMATCH");
                return status;
            }
            MQClientInstance client = implementation.getmQClientFactory();
            if (client == null || client.getMQClientAPIImpl() == null) {
                status.setError("LOCAL_SEARCH_CLIENT_UNAVAILABLE");
                return status;
            }
            status.setClientId(client.getClientId());
            ConcurrentMap<MessageQueue, ProcessQueue> localQueues = implementation.getRebalanceImpl().getProcessQueueTable();
            Map<MessageQueue, ProcessQueue> assigned = new HashMap<>();
            localQueues.forEach((queue, process) -> {
                if (topic.equals(queue.getTopic()) && process != null && !process.isDropped()) {
                    assigned.put(queue, process);
                }
            });
            status.setLocalAssignedQueueCount(assigned.size());
            // 本机只负责部分队列，发布判断还要检查消费组在整个 Topic 上的积压。
            inspectGroupQueues(client.getMQClientAPIImpl(), assigned,
                    client.selectConsumer(consumerGroup) == implementation, status);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            status.setState(TicketSearchDiagnosticState.UNKNOWN);
            status.setGroupBacklog(null);
            status.setError("CONSUMER_DIAGNOSTICS_FAILED:" + exception.getClass().getSimpleName());
        }
        return status;
    }

    /**
     * 从 NameServer 新读路由，避免只统计本机分配队列或复用过期路由漏掉新队列。
     *
     * @param api 当前已运行 RocketMQ 客户端的 Broker 查询接口
     * @param assigned 当前进程已分配的消息队列及本地处理队列映射
     * @param localRegistered 当前进程是否确认为目标消费组成员
     * @param status 接收全消费组路由、积压及完整性结果的诊断对象
     * @throws Exception 处理过程中发生异常时
     */
    private void inspectGroupQueues(MQClientAPIImpl api, Map<MessageQueue, ProcessQueue> assigned,
                                    boolean localRegistered,
                                    TicketSearchConsumerStatus status) throws Exception {
        TopicRouteData route = api.getTopicRouteInfoFromNameServer(topic, RPC_TIMEOUT_MS);
        if (route == null || route.getQueueDatas() == null || route.getBrokerDatas() == null) {
            status.setError("SEARCH_TOPIC_ROUTE_UNAVAILABLE");
            return;
        }
        Map<String, String> masters = new HashMap<>();
        for (BrokerData broker : route.getBrokerDatas()) {
            if (broker.getBrokerAddrs() != null) {
                masters.put(broker.getBrokerName(), broker.getBrokerAddrs().get(MixAll.MASTER_ID));
            }
        }
        List<TicketSearchQueueStatus> queues = new ArrayList<>();
        boolean complete = true;
        long backlog = 0;
        for (QueueData data : route.getQueueDatas()) {
            for (int queueId = 0; queueId < data.getReadQueueNums(); queueId++) {
                MessageQueue queue = new MessageQueue(topic, data.getBrokerName(), queueId);
                if (!PermName.isReadable(data.getPerm())) {
                    TicketSearchQueueStatus unreadable = new TicketSearchQueueStatus();
                    unreadable.setBrokerName(data.getBrokerName());
                    unreadable.setQueueId(queueId);
                    unreadable.setLocalAssigned(assigned.containsKey(queue));
                    unreadable.setState(TicketSearchDiagnosticState.UNKNOWN);
                    unreadable.setError("SEARCH_QUEUE_NOT_READABLE");
                    queues.add(unreadable);
                    complete = false;
                    continue;
                }
                TicketSearchQueueStatus item = inspectQueue(api, queue, masters.get(data.getBrokerName()),
                        assigned.containsKey(queue), localRegistered);
                queues.add(item);
                if (item.getState() == TicketSearchDiagnosticState.AVAILABLE) {
                    backlog = Math.addExact(backlog, item.getBacklog());
                } else {
                    complete = false;
                }
            }
        }
        status.setQueues(queues);
        status.setGroupQueueCount(queues.size());
        // 有一个队列未取得有效位置，整体积压就是未知，不能返回其余队列的部分合计。
        if (queues.isEmpty() || !complete) {
            status.setError(queues.isEmpty() ? "SEARCH_TOPIC_HAS_NO_READABLE_QUEUES" : "SEARCH_QUEUE_OFFSETS_INCOMPLETE");
            return;
        }
        status.setGroupBacklog(backlog);
        status.setState(TicketSearchDiagnosticState.AVAILABLE);
    }

    /**
     * 查询持久提交位置，不调用会回写内存消费位置的 OffsetStore.readOffset。
     *
     * @param api 当前已运行 RocketMQ 客户端的 Broker 查询接口
     * @param queue 需要观测的 Topic、Broker 与队列编号
     * @param brokerAddress 该队列所属 Broker 的当前地址
     * @param localAssigned 该队列是否分配给当前进程
     * @param localRegistered 当前进程是否属于目标消费组
     * @return 该队列的 Broker 最大位置、持久消费位置及积压诊断
     * @throws InterruptedException 处理过程中发生Interrupted异常时
     */
    private TicketSearchQueueStatus inspectQueue(MQClientAPIImpl api, MessageQueue queue, String brokerAddress,
                                                boolean localAssigned, boolean localRegistered) throws InterruptedException {
        TicketSearchQueueStatus status = new TicketSearchQueueStatus();
        status.setBrokerName(queue.getBrokerName());
        status.setQueueId(queue.getQueueId());
        status.setLocalAssigned(localAssigned);
        status.setState(TicketSearchDiagnosticState.UNKNOWN);
        if (brokerAddress == null || brokerAddress.trim().isEmpty()) {
            status.setError("BROKER_MASTER_UNAVAILABLE");
            return status;
        }
        try {
            long maximum = api.getMaxOffset(brokerAddress, queue, RPC_TIMEOUT_MS);
            status.setBrokerOffset(maximum);
            QueryConsumerOffsetRequestHeader request = new QueryConsumerOffsetRequestHeader();
            request.setTopic(topic);
            request.setConsumerGroup(consumerGroup);
            request.setQueueId(queue.getQueueId());
            request.setBname(queue.getBrokerName());
            // 这里只观测 Broker 已持久化的位置，不补写缺失记录，也不读取可能已提前推进的本地缓存。
            request.setSetZeroIfNotFound(Boolean.FALSE);
            long committed;
            try {
                committed = api.queryConsumerOffset(brokerAddress, request, RPC_TIMEOUT_MS);
            } catch (MQBrokerException exception) {
                // 新空队列尚未持久提交位置是合法状态；只接受 Broker 明确的 QUERY_NOT_FOUND。
                // 超时、鉴权失败以及非空队列的缺失记录仍属于未知，绝不按零处理。
                if (exception.getResponseCode() != ResponseCode.QUERY_NOT_FOUND
                        || maximum != 0 || !localRegistered) {
                    throw exception;
                }
                committed = 0;
                status.setEmptyQueueWithoutCommittedOffset(true);
            }
            status.setCommittedOffset(committed);
            if (maximum < 0 || committed < 0 || committed > maximum) {
                status.setError("BROKER_OFFSETS_INVALID_OR_NOT_COMPARABLE");
                return status;
            }
            status.setBacklog(maximum - committed);
            status.setState(TicketSearchDiagnosticState.AVAILABLE);
        } catch (InterruptedException exception) {
            throw exception;
        } catch (Exception exception) {
            status.setError("BROKER_OFFSET_QUERY_FAILED:" + exception.getClass().getSimpleName());
        }
        return status;
    }

    /**
     * 检查诊断计数是否为已知非负数。
     *
     * @param value 数据库返回的可空积压计数
     * @return 值非 null 且不小于 0 时为 true
     */
    private boolean nonnegative(Long value) {
        return value != null && value >= 0;
    }

    /**
     * 保留采集失败事实，构造计数未知的 Outbox 诊断。
     *
     * @param error 不含外部凭据和正文的诊断失败摘要
     * @return 计数保持未知并记录脱敏错误的 Outbox 状态
     */
    private TicketSearchOutboxStatus unknownOutbox(String error) {
        TicketSearchOutboxStatus status = new TicketSearchOutboxStatus();
        status.setState(TicketSearchDiagnosticState.UNKNOWN);
        status.setError(error);
        return status;
    }
}

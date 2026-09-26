package com.WorkOrder.messaging.consumer;

import com.WorkOrder.messaging.contract.WorkOrderEvent;
import org.springframework.transaction.annotation.Transactional;

/** 保证消费日志和业务写入位于同一个本地事务。 */
public class IdempotentConsumerExecutor {
    private final ConsumeLogMapper consumeLogMapper;

    /**
     * 创建幂等消费执行器。
     *
     * @param consumeLogMapper 消费日志数据访问组件
     */
    public IdempotentConsumerExecutor(ConsumeLogMapper consumeLogMapper) {
        this.consumeLogMapper = consumeLogMapper;
    }

    /**
     * 在同一个本地事务中登记消费日志并执行业务动作。
     * 业务动作抛出异常时，消费日志和业务数据会一起回滚，异常继续交给监听器触发重试。
     *
     * @param consumerGroup 业务消费组
     * @param event 统一事件信封
     * @param topic 消息来源 Topic
     * @param tag 消息 Tag
     * @param businessAction 需要幂等保护的业务动作
     * @return 首次消费并执行了业务动作时返回 true，重复事件返回 false
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean execute(String consumerGroup, WorkOrderEvent event, String topic,
                           String tag, Runnable businessAction) {
        if (isBlank(consumerGroup) || event == null || isBlank(event.getEventId())
                || isBlank(event.getEventType()) || isBlank(topic) || businessAction == null) {
            throw new IllegalArgumentException("消费组、事件、Topic 和业务动作不能为空");
        }
        int inserted = consumeLogMapper.insertIgnore(consumerGroup, event, topic, tag);
        if (inserted == 0) {
            return false;
        }
        businessAction.run();
        return true;
    }

    /**
     * 判断字符串是否为空或只包含空白字符。
     *
     * @param value 待判断字符串
     * @return 为空时返回 true
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

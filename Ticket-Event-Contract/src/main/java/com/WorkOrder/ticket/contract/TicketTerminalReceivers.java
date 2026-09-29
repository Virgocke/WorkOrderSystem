package com.WorkOrder.ticket.contract;

import java.util.ArrayList;
import java.util.List;

/** 关闭与撤销事件共享的接收人顺序约定。 */
final class TicketTerminalReceivers {
    /** 工具类不允许实例化。 */
    private TicketTerminalReceivers() { }

    /** 按创建人、处理人的顺序去重并排除操作人。 */
    static List<Long> of(long creatorId, Long handlerId, long actorId) {
        List<Long> result = new ArrayList<>();
        if (creatorId != actorId) {
            result.add(creatorId);
        }
        if (handlerId != null && handlerId != actorId && !handlerId.equals(creatorId)) {
            result.add(handlerId);
        }
        return result;
    }

    /** 校验生产者提供的接收人快照符合统一约定。 */
    static boolean matches(List<Long> receiverIds, long creatorId, Long handlerId, long actorId) {
        return of(creatorId, handlerId, actorId).equals(receiverIds);
    }
}

package com.WorkOrder.ticket.contract;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 关闭与撤销事件共享的接收人顺序约定。
 */
final class TicketTerminalReceivers {
    /**
     * 工具类不允许实例化。
     */
    private TicketTerminalReceivers() { }

    /**
     * 按创建人、处理人的顺序去重并排除操作人。
     *
     * @param creatorId 创建人用户 ID
     * @param handlerId 当前处理人 ID，尚未分配时可为 null
     * @param actorId 须排除的本次操作人 ID
     * @return 按创建人、处理人顺序去重并排除操作人的接收人列表
     */
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

    /**
     * 校验生产者提供的接收人快照符合统一约定。
     *
     * @param receiverIds 生产者记录的有序接收人快照
     * @param creatorId 创建人用户 ID
     * @param handlerId 当前处理人 ID，尚未分配时可为 null
     * @param actorId 本次操作的用户 ID
     * @return 接收人列表的内容和顺序均与统一终态规则完全一致时为 true
     */
    static boolean matches(List<Long> receiverIds, long creatorId, Long handlerId, long actorId) {
        return of(creatorId, handlerId, actorId).equals(receiverIds);
    }
}

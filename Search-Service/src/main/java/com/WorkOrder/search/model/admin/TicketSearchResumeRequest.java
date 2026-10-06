package com.WorkOrder.search.model.admin;

import lombok.Data;
import javax.validation.constraints.Size;

/** FAILED 普通续跑不需发布证明；PUBLISHING 恢复必须提供原令牌及停机确认。 */
@Data
public class TicketSearchResumeRequest {
    /** 从任务详情取得的原发布令牌。 */
    @Size(max=32)
    private String publisherToken;
    /** 管理员已确认原发布进程停止执行。 */
    private boolean originalPublisherStopped;
    /** 如何确认原发布进程停止的操作记录。 */
    @Size(max=500)
    private String reason;
}

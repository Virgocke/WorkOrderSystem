package com.WorkOrder.search.model.admin;

import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;

/** 发布时由管理员记录全部源实例的部署核对，Search 本机配置不能证明这些事实。 */
@Data
public class TicketSearchPublishRequest {
    /** 确认该清单覆盖全部实际写工单的源实例。 */
    @AssertTrue
    private boolean sourceInventoryComplete;
    /** 每个源实例的部署版本和事件发布状态。 */
    @Valid @NotEmpty @Size(max=30)
    private List<@NotNull SourceInstance> sourceInstances;

    /** 管理员已实际核对的单个源实例。 */
    @Data
    public static class SourceInstance {
        /** 部署系统中的实例身份。 */
        @NotBlank @Size(max=100)
        private String instanceId;
        /** 接入阶段一全部写入口的部署版本。 */
        @NotBlank @Size(max=100)
        private String deployedVersion;
        /** 搜索事件发布开关确实开启。 */
        @AssertTrue private boolean publishEnabled;
        /** Outbox 发送工作者确实开启。 */
        @AssertTrue private boolean outboxEnabled;
        /** 已核对该实例全部工单写入口。 */
        @AssertTrue private boolean eventWritePathsVerified;
    }
}

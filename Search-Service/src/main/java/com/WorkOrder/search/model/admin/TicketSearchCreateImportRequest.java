package com.WorkOrder.search.model.admin;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 创建任务请求；不允许调用者指定索引、版本或扫描范围。
 */
@Data
public class TicketSearchCreateImportRequest {
    /**
     * 网络重试必须复用的 32 位十六进制 UUID。
     */
    @Pattern(regexp="[0-9a-fA-F]{32}") @NotBlank
    private String requestId;
    /**
     * 本次导入或重建原因。
     */
    @NotBlank @Size(max=500)
    private String reason;
}

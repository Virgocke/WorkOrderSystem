package com.WorkOrder.notification.dto;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.Locale;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 同一次操作的网络重试复用 requestId；再次人工重发使用新 ID 和最新轮次。
 */
@Data
public class EmailDeliveryRetryRequest {
    @NotBlank(message = "缺少重发请求 ID")
    @Pattern(regexp = "[0-9a-fA-F]{32}", message = "重发请求 ID 必须是 32 位十六进制 UUID")
    private String requestId;
    @NotNull(message = "缺少预期重发轮次")
    @Min(value = 0, message = "预期重发轮次不能为负数")
    private Integer expectedRetryRound;
    @NotBlank(message = "请填写重发原因")
    @Size(max = 200, message = "重发原因最多 200 字")
    private String reason;

    /**
     * 服务入口再次校验并生成副本，统一幂等键大小写和审计原因。
     *
     * @return 完成字段校验、请求 ID 小写化及原因去除首尾空白后的新请求
     */
    public EmailDeliveryRetryRequest normalizedCopy() {
        if (requestId == null || !requestId.matches("[0-9a-fA-F]{32}")
                || expectedRetryRound == null || expectedRetryRound < 0
                || reason == null || reason.trim().isEmpty() || reason.length() > 200) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        EmailDeliveryRetryRequest copy = new EmailDeliveryRetryRequest();
        copy.setRequestId(requestId.toLowerCase(Locale.ROOT));
        copy.setExpectedRetryRound(expectedRetryRound);
        copy.setReason(reason.trim());
        return copy;
    }
}

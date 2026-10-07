package com.WorkOrder.user.model;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 更新处理人资料请求
 */
@Data
public class UpdateHandlerProfileRequest {
    @NotNull(message = "最大容量不能为空")
    @Min(value = 1, message = "最大容量至少为 1")
    @Max(value = 1000, message = "最大容量不能超过 1000")
    private Integer maxCapacity;
    private Set<String> skills = new LinkedHashSet<>();
}

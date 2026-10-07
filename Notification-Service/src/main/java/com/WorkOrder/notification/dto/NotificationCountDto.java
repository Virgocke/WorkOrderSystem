package com.WorkOrder.notification.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 通知数量响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCountDto {

    @ApiModelProperty(value = "通知数量", example = "5")
    private long count;
}

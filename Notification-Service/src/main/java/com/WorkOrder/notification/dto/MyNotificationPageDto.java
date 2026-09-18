package com.WorkOrder.notification.dto;

import com.WorkOrder.model.page.PageParams;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 我的通知分页查询参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MyNotificationPageDto extends PageParams {

    @ApiModelProperty(value = "阅读状态，UNREAD/READ，空表示全部")
    private String status;
}

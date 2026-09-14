package com.WorkOrder.ticket.dto;

import com.WorkOrder.model.page.PageParams;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Virgor
 * @date 2026年09月15日 01:13
 * @description 我的工单分页查询参数类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MyTicketPageDto extends PageParams {
    @ApiModelProperty(value = "工单状态，all表示全部")
    private String status = "all";
    @ApiModelProperty(value = "标题/描述/编号模糊搜索")
    private String keyword;
}

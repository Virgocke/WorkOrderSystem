package com.WorkOrder.handler.dto;

import com.WorkOrder.model.page.PageParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * @author Virgor
 * @date 2026年09月23日 00:27
 * @description 处理人分页列表查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class HandlerPageListDto extends PageParams {

    private String keyword;

    @Min(0)
    @Max(1)
    private Integer status;
}

package com.WorkOrder.model;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Virgor
 * @date 2026年09月08日 18:49
 * @description 分页查询的参数类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageParams {
    @ApiModelProperty(value = "当前页码", example = "1")
    private Long pageNo = 1L; // 当前页码
    @ApiModelProperty(value = "每页大小", example = "10")
    private Long pageSize = 10L;
}

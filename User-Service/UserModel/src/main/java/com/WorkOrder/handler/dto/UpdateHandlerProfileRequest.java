package com.WorkOrder.handler.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/** 更新处理人档案请求，所有字段均采用部分更新语义。 */
@Getter
public class UpdateHandlerProfileRequest {

    @Min(value = 1, message = "最大容量至少为 1")
    @Max(value = 1000, message = "最大容量不能超过 1000")
    private Integer maxCapacity;

    private Long departmentId;

    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;

    @JsonIgnore
    private boolean maxCapacityPresent;

    @JsonIgnore
    private boolean departmentIdPresent;

    @JsonIgnore
    private boolean statusPresent;

    @JsonSetter("maxCapacity")
    public void setMaxCapacity(Integer maxCapacity) {
        this.maxCapacity = maxCapacity;
        this.maxCapacityPresent = true;
    }

    /** 显式传入 {@code null} 时清空处理人的所属部门。 */
    @JsonSetter("departmentId")
    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
        this.departmentIdPresent = true;
    }

    @JsonSetter("status")
    public void setStatus(Integer status) {
        this.status = status;
        this.statusPresent = true;
    }
}

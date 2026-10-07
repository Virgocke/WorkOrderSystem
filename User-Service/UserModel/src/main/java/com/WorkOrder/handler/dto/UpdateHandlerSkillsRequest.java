package com.WorkOrder.handler.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 全量覆盖处理人技能的请求。空数组表示清空全部技能。
 */
@Data
public class UpdateHandlerSkillsRequest {

    @Valid
    @NotNull(message = "技能列表不能为空")
    private List<HandlerSkillRequest> skills;
}

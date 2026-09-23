package com.WorkOrder.skill.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;

import javax.validation.constraints.Size;

/** 修改技能标签请求，所有字段均采用部分更新语义。 */
@Getter
public class UpdateSkillTagRequest {

    @Size(max = 50, message = "技能名称不能超过 50 个字符")
    private String name;

    @Size(max = 255, message = "技能描述不能超过 255 个字符")
    private String description;

    @JsonIgnore
    private boolean namePresent;

    @JsonIgnore
    private boolean descriptionPresent;

    /**
     * 设置技能名称，并记录请求中显式提交了该字段。
     *
     * @param name 技能名称
     */
    @JsonSetter("name")
    public void setName(String name) {
        this.name = name;
        this.namePresent = true;
    }

    /**
     * 设置技能描述，并记录请求中显式提交了该字段；传入 {@code null} 表示清空描述。
     *
     * @param description 技能描述
     */
    @JsonSetter("description")
    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }
}

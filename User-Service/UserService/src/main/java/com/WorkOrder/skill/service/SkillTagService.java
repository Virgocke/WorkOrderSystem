package com.WorkOrder.skill.service;

import com.WorkOrder.model.handler.SkillTag;

import java.util.List;

/** 技能标签目录服务。 */
public interface SkillTagService {

    /**
     * 获取全部技能标签及其处理人使用数。
     *
     * @return 技能标签列表
     */
    List<SkillTag> listSkillTags();
}

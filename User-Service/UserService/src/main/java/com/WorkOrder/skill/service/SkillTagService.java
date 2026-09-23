package com.WorkOrder.skill.service;

import com.WorkOrder.model.handler.SkillTag;
import com.WorkOrder.skill.dto.CreateSkillTagRequest;
import com.WorkOrder.skill.dto.UpdateSkillTagRequest;

import java.util.List;

/** 技能标签目录服务。 */
public interface SkillTagService {

    /**
     * 获取全部技能标签及其处理人使用数。
     *
     * @return 技能标签列表
     */
    List<SkillTag> listSkillTags();

    /**
     * 新增技能标签。
     *
     * @param request 技能创建参数
     * @return 新建后的技能标签
     */
    SkillTag createSkillTag(CreateSkillTagRequest request);

    /**
     * 部分更新技能标签。
     *
     * @param id 技能 ID
     * @param request 待更新字段
     * @return 更新后的技能标签
     */
    SkillTag updateSkillTag(Long id, UpdateSkillTagRequest request);

    /**
     * 删除未被处理人使用的技能标签。
     *
     * @param id 技能 ID
     * @return 删除成功时返回 {@code true}
     */
    boolean deleteSkillTag(Long id);
}

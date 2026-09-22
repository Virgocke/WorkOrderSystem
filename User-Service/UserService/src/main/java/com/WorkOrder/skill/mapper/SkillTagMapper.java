package com.WorkOrder.skill.mapper;

import com.WorkOrder.model.handler.SkillTag;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** 技能标签查询 Mapper。 */
@Mapper
public interface SkillTagMapper {

    /**
     * 查询全部技能标签及各标签的处理人使用数。
     *
     * @return 按技能 ID 升序排列的标签列表
     */
    List<SkillTag> selectSkillTagsWithUsedCount();
}

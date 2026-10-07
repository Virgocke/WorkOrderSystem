package com.WorkOrder.skill.mapper;

import com.WorkOrder.model.handler.SkillTag;
import com.WorkOrder.skill.model.SkillTagRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 技能标签查询 Mapper。
 */
@Mapper
public interface SkillTagMapper extends BaseMapper<SkillTagRecord> {

    /**
     * 查询全部技能标签及各标签的处理人使用数。
     *
     * @return 按技能 ID 升序排列的标签列表
     */
    List<SkillTag> selectSkillTagsWithUsedCount();

    /**
     * 按 ID 查询技能标签及处理人使用数。
     *
     * @param id 技能 ID
     * @return 技能标签；不存在时返回 {@code null}
     */
    SkillTag selectSkillTagById(@Param("id") Long id);

    /**
     * 查询同名技能数量，可在更新时排除当前技能。
     *
     * @param name 规范化后的技能名称
     * @param excludeId 排除的技能 ID；新增时为 {@code null}
     * @return 同名技能数量
     */
    int countByNameExcludingId(@Param("name") String name,
                               @Param("excludeId") Long excludeId);

    /**
     * 新增技能标签并回填数据库生成的 ID。
     *
     * @param skillTag 待新增技能
     * @return 受影响行数
     */
    int insertSkillTag(SkillTag skillTag);

    /**
     * 部分更新技能标签。
     *
     * @param id 技能 ID
     * @param name 技能名称
     * @param description 技能描述
     * @param updateName 是否更新名称
     * @param updateDescription 是否更新描述
     * @return 受影响行数
     */
    int updateSkillTag(@Param("id") Long id,
                       @Param("name") String name,
                       @Param("description") String description,
                       @Param("updateName") boolean updateName,
                       @Param("updateDescription") boolean updateDescription);

    /**
     * 仅在技能未被处理人或工单分类使用时删除。
     *
     * @param id 技能 ID
     * @return 受影响行数
     */
    int deleteSkillTagIfUnused(@Param("id") Long id);
}

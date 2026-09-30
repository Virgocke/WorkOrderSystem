package com.WorkOrder.ticket.mapper;

import com.WorkOrder.ticket.model.TicketCategory;
import com.WorkOrder.ticket.model.TicketCategorySkillRelation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月13日 03:08
 * @description 工单分类Mapper
 */
@Mapper
public interface TicketCategoryMapper extends BaseMapper<TicketCategory> {
    /** 查询全部分类技能关系，用于分类树一次性填充技能配置。 */
    @Select("SELECT category_id AS categoryId, skill_tag_id AS skillTagId FROM ticket_category_skills ORDER BY category_id, skill_tag_id")
    List<TicketCategorySkillRelation> selectAllSkillRelations();

    /** 查询指定分类直接要求的技能标签 ID。 */
    @Select("SELECT skill_tag_id FROM ticket_category_skills WHERE category_id = #{categoryId} ORDER BY skill_tag_id")
    List<Long> selectSkillIds(@Param("categoryId") Long categoryId);

    /** 检查提交的技能标签 ID 是否都存在。 */
    @Select({"<script>", "SELECT COUNT(*) FROM skill_tags WHERE id IN",
            "<foreach collection='skillIds' item='skillId' open='(' separator=',' close=')'>#{skillId}</foreach>",
            "</script>"})
    int countSkillTags(@Param("skillIds") List<Long> skillIds);

    /** 清除当前分类的直接技能配置，子分类仍可继承祖先配置。 */
    @Delete("DELETE FROM ticket_category_skills WHERE category_id = #{categoryId}")
    int deleteSkillIds(@Param("categoryId") Long categoryId);

    /** 为当前分类加入一条技能要求。 */
    @Insert("INSERT INTO ticket_category_skills (category_id, skill_tag_id) VALUES (#{categoryId}, #{skillId})")
    int insertSkillId(@Param("categoryId") Long categoryId, @Param("skillId") Long skillId);
}

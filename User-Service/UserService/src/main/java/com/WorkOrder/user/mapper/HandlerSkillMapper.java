package com.WorkOrder.user.mapper;

import com.WorkOrder.user.model.HandlerSkill;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author Virgor
 * @date 2026年09月22日 01:24
 * @description 持久化处理人的技能及熟练度。
 */
@Mapper
public interface HandlerSkillMapper extends BaseMapper<HandlerSkill> {
}

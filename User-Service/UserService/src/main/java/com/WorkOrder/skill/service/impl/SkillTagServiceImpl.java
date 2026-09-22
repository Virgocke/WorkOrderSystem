package com.WorkOrder.skill.service.impl;

import com.WorkOrder.model.handler.SkillTag;
import com.WorkOrder.skill.mapper.SkillTagMapper;
import com.WorkOrder.skill.service.SkillTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/** 技能标签目录服务实现。 */
@Service
@RequiredArgsConstructor
public class SkillTagServiceImpl implements SkillTagService {

    private final SkillTagMapper skillTagMapper;

    /** 按接口文档 16.1 查询技能标签列表。 */
    @Override
    @Transactional(readOnly = true)
    public List<SkillTag> listSkillTags() {
        List<SkillTag> skills = skillTagMapper.selectSkillTagsWithUsedCount();
        return skills == null ? Collections.emptyList() : skills;
    }
}

package com.WorkOrder.skill.service.impl;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.handler.SkillTag;
import com.WorkOrder.skill.dto.CreateSkillTagRequest;
import com.WorkOrder.skill.dto.UpdateSkillTagRequest;
import com.WorkOrder.skill.mapper.SkillTagMapper;
import com.WorkOrder.skill.service.SkillTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

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

    /** 按接口文档 16.2 新增技能标签。 */
    @Override
    @Transactional
    public SkillTag createSkillTag(CreateSkillTagRequest request) {
        String name = normalizeName(request.getName());
        ensureUniqueName(name, null);

        SkillTag skillTag = SkillTag.builder()
                .name(name)
                .description(request.getDescription())
                .build();
        try {
            if (skillTagMapper.insertSkillTag(skillTag) != 1 || skillTag.getId() == null) {
                throw new SystemException(SystemExceptionEnum.CREATE_FAILED);
            }
        } catch (DuplicateKeyException exception) {
            throw new SystemException("技能名称已存在");
        }
        return getRequiredSkillTag(skillTag.getId());
    }

    /** 按接口文档 16.2 部分更新技能标签。 */
    @Override
    @Transactional
    public SkillTag updateSkillTag(Long id, UpdateSkillTagRequest request) {
        requireValidId(id);
        SkillTag current = getRequiredSkillTag(id);
        if (!request.isNamePresent() && !request.isDescriptionPresent()) {
            return current;
        }

        String name = null;
        if (request.isNamePresent()) {
            name = normalizeName(request.getName());
            ensureUniqueName(name, id);
        }
        try {
            if (skillTagMapper.updateSkillTag(id, name, request.getDescription(),
                    request.isNamePresent(), request.isDescriptionPresent()) != 1) {
                throw new NoSuchElementException("技能不存在");
            }
        } catch (DuplicateKeyException exception) {
            throw new SystemException("技能名称已存在");
        }
        return getRequiredSkillTag(id);
    }

    /** 按接口文档 16.2 删除未被处理人引用的技能标签。 */
    @Override
    @Transactional
    public boolean deleteSkillTag(Long id) {
        requireValidId(id);
        SkillTag current = getRequiredSkillTag(id);
        if (current.getUsedCount() != null && current.getUsedCount() > 0) {
            throw new SystemException("该技能已分配给处理人，无法删除");
        }
        if (skillTagMapper.deleteSkillTagIfUnused(id) != 1) {
            SkillTag latest = skillTagMapper.selectSkillTagById(id);
            if (latest == null) {
                throw new NoSuchElementException("技能不存在");
            }
            throw new SystemException("该技能已分配给处理人，无法删除");
        }
        return true;
    }

    /** 去除技能名称首尾空白并校验名称非空。 */
    private String normalizeName(String name) {
        String normalized = name == null ? null : name.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new SystemException("技能名称不能为空");
        }
        return normalized;
    }

    /** 校验技能名称在数据库中唯一。 */
    private void ensureUniqueName(String name, Long excludeId) {
        if (skillTagMapper.countByNameExcludingId(name, excludeId) > 0) {
            throw new SystemException("技能名称已存在");
        }
    }

    /** 查询指定技能；不存在时抛出可转换为 404 的异常。 */
    private SkillTag getRequiredSkillTag(Long id) {
        SkillTag skillTag = skillTagMapper.selectSkillTagById(id);
        if (skillTag == null) {
            throw new NoSuchElementException("技能不存在");
        }
        return skillTag;
    }

    /** 校验技能 ID 为正整数。 */
    private void requireValidId(Long id) {
        if (id == null || id <= 0) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
    }
}

package com.WorkOrder.ticket.service.impl;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.ticket.dto.TicketCategoryDto;
import com.WorkOrder.ticket.dto.TicketCategoryTreeDto;
import com.WorkOrder.ticket.mapper.TicketCategoryMapper;
import com.WorkOrder.ticket.model.TicketCategorySkillRelation;
import com.WorkOrder.ticket.model.TicketCategory;
import com.WorkOrder.ticket.service.TicketCategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Virgor
 * @date 2026年09月13日 03:55
 * @description 工单类别服务实现
 */
@Service
@RequiredArgsConstructor
public class TicketCategoryServiceImpl implements TicketCategoryService {

    private final TicketCategoryMapper ticketCategoryMapper;

    /**
     * 仅查询所需分类的 ID 和名称，重复 ID 合并为一次批量查询。
     *
     * @param categoryIds 待查询名称的分类 ID
     * @return 当前分类名称映射，空集合不访问数据库
     */
    @Override
    public Map<Long, String> getCategoryNamesByIds(Collection<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> ids = categoryIds.stream().filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<TicketCategory> categories = ticketCategoryMapper.selectList(new LambdaQueryWrapper<TicketCategory>()
                .select(TicketCategory::getId, TicketCategory::getName)
                .in(TicketCategory::getId, ids));
        Map<Long, String> namesById = new HashMap<>();
        for (TicketCategory category : categories) {
            namesById.put(category.getId(), category.getName());
        }
        return namesById;
    }

    /**
     * 查询分类关系后遍历全部后代，已访问集合同时防止错误父子关系形成循环。
     * 只读 ID 与父 ID，不加载分类技能或展示字段。
     *
     * @param categoryId 所选分类 ID
     * @return 包含自身的去重分类 ID 列表
     */
    @Override
    public List<Long> getCategoryIdsInSubtree(Long categoryId) {
        List<TicketCategory> categories = ticketCategoryMapper.selectList(new LambdaQueryWrapper<TicketCategory>()
                .select(TicketCategory::getId, TicketCategory::getParentId));
        Map<Long, List<Long>> childrenByParent = categories.stream()
                .filter(category -> category.getParentId() != null)
                .collect(Collectors.groupingBy(TicketCategory::getParentId,
                        Collectors.mapping(TicketCategory::getId, Collectors.toList())));
        Set<Long> visited = new LinkedHashSet<>();
        Deque<Long> pending = new ArrayDeque<>();
        pending.add(categoryId);
        while (!pending.isEmpty()) {
            Long id = pending.removeFirst();
            if (visited.add(id) && childrenByParent.containsKey(id)) {
                pending.addAll(childrenByParent.get(id));
            }
        }
        return new ArrayList<>(visited);
    }

    /**
     * 获取工单类别树
     *
     * @return 工单类别树
     */
    @Override
    public List<TicketCategoryTreeDto> getTicketCategoryTree() {
        // 查询所有工单类别
        List<TicketCategory> ticketCategoryTree = ticketCategoryMapper.selectList(null);

        // 构建工单类别树
        Map<Long, TicketCategoryTreeDto> nodeMap = ticketCategoryTree.stream()
                .collect(Collectors.toMap(
                        TicketCategory::getId,
                        TicketCategoryTreeDto::from
                ));
        for (TicketCategorySkillRelation relation : ticketCategoryMapper.selectAllSkillRelations()) {
            TicketCategoryTreeDto node = nodeMap.get(relation.getCategoryId());
            if (node != null) {
                node.getRequiredSkillIds().add(relation.getSkillTagId());
            }
        }
        // 构建树结构
        List<TicketCategoryTreeDto> roots = new ArrayList<>();
        // 遍历所有节点，构建树结构
        for (TicketCategory ticketCategory : ticketCategoryTree){
            // 获取当前节点
            TicketCategoryTreeDto node = nodeMap.get(ticketCategory.getId());
            // 如果当前节点的父节点为空，则当前节点为根节点
            if(ticketCategory.getParentId() == null){
                roots.add(node);
            } else {
                // 获取当前节点的父节点
                TicketCategoryTreeDto parent = nodeMap.get(ticketCategory.getParentId());
                if (parent != null) {
                    // 将当前节点添加到父节点的子节点列表中
                    parent.getChildren().add(node);
                }
            }
        }
        return roots;
    }

    /**
     * 创建工单类别
     *
     * @param ticketCategoryDto 工单分类请求数据
     * @return 创建的工单类别
     */
    @Transactional
    @Override
    public TicketCategory createTicketCategory(TicketCategoryDto ticketCategoryDto) {
        validateSkills(ticketCategoryDto.getRequiredSkillIds());
        // 检查工单类别名称是否已存在
        TicketCategory ticketCategory = ticketCategoryMapper.selectOne(
                new LambdaQueryWrapper<TicketCategory>()
                        .eq(TicketCategory::getName, ticketCategoryDto.getName())
                        .eq(TicketCategory::getParentId, ticketCategoryDto.getParentId())
                        .last("limit 1"));

        if (ticketCategory != null){
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }


        ticketCategory = new TicketCategory();
        BeanUtils.copyProperties(ticketCategoryDto, ticketCategory);
        int result = ticketCategoryMapper.insert(ticketCategory);
        if (result != 1){
            throw new SystemException(SystemExceptionEnum.CREATE_FAILED);
        } else {
            ticketCategory = ticketCategoryMapper.selectOne(
                    new LambdaQueryWrapper<TicketCategory>()
                            .eq(TicketCategory::getName, ticketCategoryDto.getName())
                            .eq(TicketCategory::getParentId, ticketCategoryDto.getParentId())
            );
        }
        replaceSkills(ticketCategory.getId(), ticketCategoryDto.getRequiredSkillIds());
        ticketCategory.setRequiredSkillIds(ticketCategoryMapper.selectSkillIds(ticketCategory.getId()));
        return ticketCategory;
    }

    /**
     * 更新工单类别
     *
     * @param id 工单 ID
     * @param ticketCategoryDto 工单分类请求数据
     * @return 更新的工单类别
     */
    @Override
    @Transactional
    public TicketCategory updateTicketCategory(Long id, TicketCategoryDto ticketCategoryDto) {
        validateSkills(ticketCategoryDto.getRequiredSkillIds());
        // 根据ID查询工单类别
        TicketCategory ticketCategory = ticketCategoryMapper.selectById(id);
        if (ticketCategory == null) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        // 更新工单类别
        BeanUtils.copyProperties(ticketCategoryDto, ticketCategory);
        int result = ticketCategoryMapper.updateById(ticketCategory);
        if (result != 1){
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        replaceSkills(id, ticketCategoryDto.getRequiredSkillIds());
        ticketCategory.setRequiredSkillIds(ticketCategoryMapper.selectSkillIds(id));
        return ticketCategory;
    }

    /**
     * 删除工单类别
     *
     * @param id 工单 ID
     * @return 是否删除成功
     */
    @Override
    public Boolean deleteTicketCategory(Long id) {
        // 根据ID查询工单类别
        TicketCategory ticketCategory = ticketCategoryMapper.selectById(id);
        if (ticketCategory == null) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        // 删除工单类别
        int result = ticketCategoryMapper.deleteById(id);
        if (result != 1){
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        return true;
    }

    /**
     * 验证技能 ID 均为已存在且不重复的标签。
     *
     * @param skillIds 技能 ID 集合
     */
    private void validateSkills(List<Long> skillIds) {
        if (skillIds == null) {
            return;
        }
        Set<Long> unique = new HashSet<>(skillIds);
        if (unique.size() != skillIds.size()
                || skillIds.stream().anyMatch(id -> id == null || id <= 0)
                || (!skillIds.isEmpty() && ticketCategoryMapper.countSkillTags(skillIds) != skillIds.size())) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
    }

    /**
     * 仅在请求显式给出 requiredSkillIds 时覆盖分类技能配置。
     *
     * @param categoryId 工单分类 ID
     * @param skillIds 技能 ID 集合
     */
    private void replaceSkills(Long categoryId, List<Long> skillIds) {
        if (skillIds == null) {
            return;
        }
        ticketCategoryMapper.deleteSkillIds(categoryId);
        for (Long skillId : skillIds) {
            if (ticketCategoryMapper.insertSkillId(categoryId, skillId) != 1) {
                throw new SystemException(SystemExceptionEnum.CREATE_FAILED);
            }
        }
    }
}

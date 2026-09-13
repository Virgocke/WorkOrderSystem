package com.WorkOrder.ticket.service.impl;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.ticket.dto.TicketCategoryDto;
import com.WorkOrder.ticket.dto.TicketCategoryTreeDto;
import com.WorkOrder.ticket.mapper.TicketCategoryMapper;
import com.WorkOrder.ticket.model.TicketCategory;
import com.WorkOrder.ticket.service.TicketCategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
     * 获取工单类别树
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
     * @param ticketCategoryDto
     * @return 创建的工单类别
     */
    @Transactional
    @Override
    public TicketCategory createTicketCategory(TicketCategoryDto ticketCategoryDto) {
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
        return ticketCategory;
    }

    /**
     * 更新工单类别
     * @param id
     * @param ticketCategoryDto
     * @return 更新的工单类别
     */
    @Override
    public TicketCategory updateTicketCategory(Long id, TicketCategoryDto ticketCategoryDto) {
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
        return ticketCategory;
    }

    /**
     * 删除工单类别
     * @param id
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
}

package com.WorkOrder.user.service.impl;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.handler.Department;
import com.WorkOrder.user.dto.CreateDepartmentDto;
import com.WorkOrder.user.dto.UpdateDepartmentDto;
import com.WorkOrder.user.mapper.DepartmentMapper;
import com.WorkOrder.user.service.DepartmentDirectoryService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 部门目录查询服务实现。 */
@Service
@RequiredArgsConstructor
public class DepartmentDirectoryServiceImpl implements DepartmentDirectoryService {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DepartmentMapper departmentMapper;

    /**
     * 一次查询全部部门并在内存中组装树，避免逐级查询产生 N+1 问题。
     * 缺少父节点或错误地指向自身的部门按根节点返回，防止脏数据导致节点丢失。
     *
     * @return 按部门 ID 升序排列的部门树
     */
    @Override
    public List<Department> getDepartmentTree() {
        // 查询所有部门并按 ID 升序排列
        List<com.WorkOrder.handler.model.Department> entities = departmentMapper.selectList(
                new QueryWrapper<com.WorkOrder.handler.model.Department>().orderByAsc("id"));
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        // 将部门实体转换为响应对象并存储在 Map 中，键为部门 ID
        Map<Long, Department> nodes = new LinkedHashMap<>();
        for (com.WorkOrder.handler.model.Department entity : entities) {
            nodes.put(entity.getId(), toResponse(entity));
        }

        // 按父节点分类部门节点并存储在 Map 中，键为父部门 ID
        Map<Long, List<Department>> childrenByParent = new LinkedHashMap<>();
        List<Department> roots = new ArrayList<>();
        for (com.WorkOrder.handler.model.Department entity : entities) {
            Department node = nodes.get(entity.getId());
            Long parentId = entity.getParentId();
            if (parentId == null || parentId.equals(entity.getId()) || !nodes.containsKey(parentId)) {
                roots.add(node);
                continue;
            }
            // 将部门节点添加到其父节点的子部门列表中
            childrenByParent.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(node);
        }

        // 将子部门列表设置到每个部门节点中
        for (Map.Entry<Long, Department> entry : nodes.entrySet()) {
            // 获取部门节点的子部门列表
            List<Department> children = childrenByParent.get(entry.getKey());
            // 设置部门节点的子部门列表
            entry.getValue().setChildren(children == null
                    ? new Department[0]
                    : children.toArray(new Department[0]));
        }
        return roots;
    }

    /**
     * 新建部门。父部门和负责人均为可选字段，但传入时必须指向已有记录。
     *
     * @param request 部门创建参数
     * @return 新建后的部门信息
     */
    @Override
    @Transactional
    public Department createDepartment(CreateDepartmentDto request) {
        validateParent(null, request.getParentId());
        validateManager(request.getManagerId());

        com.WorkOrder.handler.model.Department entity =
                new com.WorkOrder.handler.model.Department();
        entity.setName(normalizeName(request.getName()));
        entity.setParentId(request.getParentId());
        entity.setManagerId(request.getManagerId());
        if (departmentMapper.insert(entity) != 1 || entity.getId() == null) {
            throw new SystemException(SystemExceptionEnum.CREATE_FAILED);
        }
        return getRequiredDepartment(entity.getId());
    }

    /**
     * 部分更新部门。修改父部门时校验完整父链，禁止指向自身或自己的后代。
     *
     * @param id 部门 ID
     * @param request 待更新字段
     * @return 更新后的部门信息
     */
    @Override
    @Transactional
    public Department updateDepartment(Long id, UpdateDepartmentDto request) {
        requireValidId(id);
        com.WorkOrder.handler.model.Department entity = departmentMapper.selectById(id);
        if (entity == null) {
            throw new SystemException(SystemExceptionEnum.RESOURCE_NOT_FOUND);
        }

        UpdateWrapper<com.WorkOrder.handler.model.Department> update = new UpdateWrapper<>();
        update.eq("id", id);
        boolean changed = false;
        if (request.isNamePresent()) {
            update.set("name", normalizeName(request.getName()));
            changed = true;
        }
        if (request.isParentIdPresent()) {
            validateParent(id, request.getParentId());
            update.set("parent_id", request.getParentId());
            changed = true;
        }
        if (request.isManagerIdPresent()) {
            validateManager(request.getManagerId());
            update.set("manager_id", request.getManagerId());
            changed = true;
        }

        if (!changed) {
            return toResponse(entity);
        }
        if (departmentMapper.update(null, update) != 1) {
            throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
        }
        return getRequiredDepartment(id);
    }

    /**
     * 仅删除叶子部门，并在删除前显式检查是否仍有用户挂靠。
     *
     * @param id 部门 ID
     * @return 删除成功时返回 {@code true}
     */
    @Override
    @Transactional
    public boolean deleteDepartment(Long id) {
        requireValidId(id);
        if (departmentMapper.selectById(id) == null) {
            throw new SystemException(SystemExceptionEnum.RESOURCE_NOT_FOUND);
        }

        Integer childCount = departmentMapper.selectCount(
                new QueryWrapper<com.WorkOrder.handler.model.Department>()
                        .eq("parent_id", id));
        if (childCount != null && childCount > 0) {
            throw new SystemException("该部门存在子部门，无法删除");
        }
        if (departmentMapper.countUsersByDepartmentId(id) > 0) {
            throw new SystemException("该部门存在挂靠用户，无法删除");
        }
        if (departmentMapper.deleteById(id) != 1) {
            throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
        }
        return true;
    }

    /**
     * 校验上级部门存在且不会使部门层级形成循环。
     *
     * @param departmentId 当前部门 ID；新增部门时为 {@code null}
     * @param parentId 待设置的上级部门 ID
     */
    private void validateParent(Long departmentId, Long parentId) {
        if (parentId == null) {
            return;
        }
        requireValidId(parentId);
        if (parentId.equals(departmentId)) {
            throw new SystemException("上级部门不能是当前部门");
        }

        Set<Long> visited = new HashSet<>();
        Long currentId = parentId;
        while (currentId != null) {
            if (!visited.add(currentId) || currentId.equals(departmentId)) {
                throw new SystemException("上级部门不能是当前部门的下级部门");
            }
            com.WorkOrder.handler.model.Department current =
                    departmentMapper.selectById(currentId);
            if (current == null) {
                throw new SystemException("上级部门不存在");
            }
            currentId = current.getParentId();
        }
    }

    /**
     * 校验部门负责人对应的用户是否存在。
     *
     * @param managerId 部门负责人用户 ID；不指定时为 {@code null}
     */
    private void validateManager(Long managerId) {
        if (managerId != null && departmentMapper.countUserById(managerId) == 0) {
            throw new SystemException("部门负责人不存在");
        }
    }

    /**
     * 去除部门名称首尾空白并校验名称非空。
     *
     * @param name 原始部门名称
     * @return 规范化后的部门名称
     */
    private String normalizeName(String name) {
        String normalized = name == null ? null : name.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new SystemException("部门名称不能为空");
        }
        return normalized;
    }

    /**
     * 校验资源 ID 为正整数。
     *
     * @param id 待校验的资源 ID
     */
    private void requireValidId(Long id) {
        if (id == null || id <= 0) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
    }

    /**
     * 查询指定部门并转换为接口响应对象。
     *
     * @param id 部门 ID
     * @return 部门响应对象
     */
    private Department getRequiredDepartment(Long id) {
        com.WorkOrder.handler.model.Department entity = departmentMapper.selectById(id);
        if (entity == null) {
            throw new SystemException(SystemExceptionEnum.RESOURCE_NOT_FOUND);
        }
        return toResponse(entity);
    }

    /**
     * 将持久化实体转换为接口响应对象。
     *
     * @param entity 部门持久化实体
     * @return 部门响应对象
     */
    private Department toResponse(
            com.WorkOrder.handler.model.Department entity) {
        Department response = new Department();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setParentId(entity.getParentId());
        response.setManagerId(entity.getManagerId());
        response.setCreatedAt(entity.getCreatedAt() == null
                ? null
                : entity.getCreatedAt().format(TIME_FORMATTER));
        response.setChildren(new Department[0]);
        return response;
    }
}

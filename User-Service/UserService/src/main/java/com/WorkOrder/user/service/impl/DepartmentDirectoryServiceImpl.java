package com.WorkOrder.user.service.impl;

import com.WorkOrder.model.handler.Department;
import com.WorkOrder.user.mapper.DepartmentMapper;
import com.WorkOrder.user.service.DepartmentDirectoryService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        List<com.WorkOrder.handler.model.Department> entities = departmentMapper.selectList(
                new QueryWrapper<com.WorkOrder.handler.model.Department>().orderByAsc("id"));
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Department> nodes = new LinkedHashMap<>();
        for (com.WorkOrder.handler.model.Department entity : entities) {
            nodes.put(entity.getId(), toResponse(entity));
        }

        Map<Long, List<Department>> childrenByParent = new LinkedHashMap<>();
        List<Department> roots = new ArrayList<>();
        for (com.WorkOrder.handler.model.Department entity : entities) {
            Department node = nodes.get(entity.getId());
            Long parentId = entity.getParentId();
            if (parentId == null || parentId.equals(entity.getId()) || !nodes.containsKey(parentId)) {
                roots.add(node);
                continue;
            }
            childrenByParent.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(node);
        }

        for (Map.Entry<Long, Department> entry : nodes.entrySet()) {
            List<Department> children = childrenByParent.get(entry.getKey());
            entry.getValue().setChildren(children == null
                    ? new Department[0]
                    : children.toArray(new Department[0]));
        }
        return roots;
    }

    /** 将持久化实体转换为接口响应对象。 */
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

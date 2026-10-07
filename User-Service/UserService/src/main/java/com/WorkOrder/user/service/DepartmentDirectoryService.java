package com.WorkOrder.user.service;

import com.WorkOrder.model.handler.Department;
import com.WorkOrder.user.dto.CreateDepartmentDto;
import com.WorkOrder.user.dto.UpdateDepartmentDto;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 部门目录查询服务。
 */
public interface DepartmentDirectoryService {

    /**
     * 查询全部部门，并按父子关系组装为树。
     *
     * @return 部门树；没有部门时返回空列表
     */
    List<Department> getDepartmentTree();

    /**
     * 新建部门。
     *
     * @param request 部门创建参数
     * @return 新建后的部门信息
     */
    Department createDepartment(CreateDepartmentDto request);

    /**
     * 部分更新指定部门。
     *
     * @param id 部门 ID
     * @param request 待更新字段
     * @return 更新后的部门信息
     */
    Department updateDepartment(Long id, UpdateDepartmentDto request);

    /**
     * 删除无子部门且无用户挂靠的部门。
     *
     * @param id 部门 ID
     * @return 删除成功时返回 {@code true}
     */
    boolean deleteDepartment(Long id);
}

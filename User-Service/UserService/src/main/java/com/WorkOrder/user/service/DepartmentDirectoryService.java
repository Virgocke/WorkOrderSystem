package com.WorkOrder.user.service;

import com.WorkOrder.model.handler.Department;

import java.util.List;

/** 部门目录查询服务。 */
public interface DepartmentDirectoryService {

    /**
     * 查询全部部门，并按父子关系组装为树。
     *
     * @return 部门树；没有部门时返回空列表
     */
    List<Department> getDepartmentTree();
}

package com.WorkOrder.controller;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.handler.Department;
import com.WorkOrder.user.service.DepartmentDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理员部门管理接口。 */
@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentDirectoryService departmentDirectoryService;

    /**
     * 获取完整部门树。
     *
     * @return 部门树
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/tree")
    public Result<List<Department>> getDepartmentTree() {
        return Result.success(departmentDirectoryService.getDepartmentTree());
    }
}

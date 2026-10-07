package com.WorkOrder.controller;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.handler.Department;
import com.WorkOrder.user.dto.CreateDepartmentDto;
import com.WorkOrder.user.dto.UpdateDepartmentDto;
import com.WorkOrder.user.service.DepartmentDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 管理员部门管理接口。
 */
@Validated
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

    /**
     * 新增部门。
     *
     * @param request 部门创建参数
     * @return 新建后的部门信息
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result<Department> createDepartment(
            @Valid @RequestBody CreateDepartmentDto request) {
        return Result.success(departmentDirectoryService.createDepartment(request));
    }

    /**
     * 修改部门的可编辑字段。
     *
     * @param id 部门 ID
     * @param request 待更新字段
     * @return 更新后的部门信息
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Result<Department> updateDepartment(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDepartmentDto request) {
        return Result.success(departmentDirectoryService.updateDepartment(id, request));
    }

    /**
     * 删除无子部门且无用户挂靠的部门。
     *
     * @param id 部门 ID
     * @return 删除成功时返回 {@code true}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteDepartment(@PathVariable Long id) {
        return Result.success(departmentDirectoryService.deleteDepartment(id));
    }
}

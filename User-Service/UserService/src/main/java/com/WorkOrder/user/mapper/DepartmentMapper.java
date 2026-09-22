package com.WorkOrder.user.mapper;

import com.WorkOrder.handler.model.Department;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @author Virgor
 * @date 2026年09月22日 01:15
 * @description 部门Mapper接口
 */
@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {

    /**
     * 查询指定部门当前挂靠的用户数。
     *
     * @param departmentId 部门 ID
     * @return 挂靠用户数量
     */
    @Select("SELECT COUNT(*) FROM users WHERE department_id = #{departmentId}")
    int countUsersByDepartmentId(@Param("departmentId") Long departmentId);

    /**
     * 查询指定用户是否存在，用于校验部门负责人。
     *
     * @param userId 用户 ID
     * @return 匹配的用户数量
     */
    @Select("SELECT COUNT(*) FROM users WHERE id = #{userId}")
    int countUserById(@Param("userId") Long userId);
}

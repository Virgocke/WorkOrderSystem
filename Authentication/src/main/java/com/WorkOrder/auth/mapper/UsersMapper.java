package com.WorkOrder.auth.mapper;

import com.WorkOrder.auth.model.AuthenticatedUser;
import com.WorkOrder.user.model.Users;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月10日 01:07
 * @description 登录Mapper接口
 */
@Mapper
public interface UsersMapper extends BaseMapper<Users> {

    /**
     * 查询登录校验需要的账号、密码、角色和状态。
     *
     * @param username 登录账号
     * @return 数据库账号；账号不存在时返回 null
     */
    @Select("SELECT id, username, password, role, status " +
            "FROM users WHERE username = #{username} LIMIT 1")
    Users findByUsername(@Param("username") String username);

    /**
     * 查询前端登录态所需的用户资料，密码字段不会进入返回对象。
     *
     * @param username 已认证的登录账号
     * @return 不含密码的用户资料；账号不存在时返回 null
     */
    @Select("SELECT u.id, u.username, u.real_name AS realName, u.email, u.phone, " +
            "u.department_id AS departmentId, d.name AS departmentName, " +
            "u.role, u.status, u.created_at AS createdAt " +
            "FROM users u LEFT JOIN departments d ON d.id = u.department_id " +
            "WHERE u.username = #{username} LIMIT 1")
    AuthenticatedUser findProfileByUsername(@Param("username") String username);

    /**
     * 优先使用 user_roles 中的显式角色；尚未写入关联表的旧数据则回退到 users.role。
     *
     * @param userId 用户主键
     * @param role users.role 中的兼容角色值
     * @return 已启用的权限编码，按权限排序号排列
     */
    @Select("SELECT p.code " +
            "FROM roles r " +
            "JOIN role_permissions rp ON rp.role_id = r.id " +
            "JOIN permissions p ON p.id = rp.permission_id " +
            "WHERE r.status = 1 AND p.status = 1 AND (" +
            "EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = #{userId} AND ur.role_id = r.id) " +
            "OR (NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = #{userId}) " +
            "AND r.code = CASE #{role} WHEN 2 THEN 'ADMIN' WHEN 1 THEN 'HANDLER' ELSE 'USER' END)) " +
            "GROUP BY p.code ORDER BY MIN(p.sort_order), p.code")
    List<String> findPermissionCodes(@Param("userId") Long userId, @Param("role") int role);

    /**
     * 根据邮箱查询用户
     * @param email
     * @return 用户信息
     */
    @Select("SELECT id, username, role, status " +
            "FROM users WHERE email = #{email} LIMIT 1")
    Users selectByEmail(@Param("email") String email);
}

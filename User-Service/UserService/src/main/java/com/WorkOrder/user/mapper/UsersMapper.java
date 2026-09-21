package com.WorkOrder.user.mapper;

import com.WorkOrder.user.dto.UsersDto;
import com.WorkOrder.model.user.UserResponse;
import com.WorkOrder.user.model.Users;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月09日 23:23
 * @description
 */
@Mapper
public interface UsersMapper extends BaseMapper<Users> {
    /**
     * 分页查询管理员用户列表。返回对象不包含密码，并关联部门名称。
     *
     * @param page 分页参数
     * @param keyword 账号、姓名、邮箱或手机号关键字
     * @param role 可选角色
     * @param status 可选状态
     * @return 用户分页数据
     */
    Page<UserResponse> selectUserPage(Page<UserResponse> page,
                                      @Param("keyword") String keyword,
                                      @Param("role") Integer role,
                                      @Param("status") Integer status);

    /**
     * 查询前端登录态所需的用户资料，密码字段不会进入返回对象。
     *
     * @param username 已认证的登录账号
     * @return 不含密码的用户资料；账号不存在时返回 null
     */
    UsersDto findProfileByUsername(@Param("username") String username);

    /**
     * 优先使用 user_roles 中的显式角色；尚未写入关联表的旧数据则回退到 users.role。
     *
     * @param userId 用户主键
     * @param role users.role 中的兼容角色值
     * @return 已启用的权限编码，按权限排序号排列
     */
    List<String> findPermissionCodes(@Param("userId") Long userId, @Param("role") int role);
}

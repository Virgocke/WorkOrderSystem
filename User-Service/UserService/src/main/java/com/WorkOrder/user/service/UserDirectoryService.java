package com.WorkOrder.user.service;

import com.WorkOrder.user.dto.AdminUpdateUserDto;
import com.WorkOrder.user.dto.CreateUserDto;
import com.WorkOrder.user.model.CreateUserRequest;
import com.WorkOrder.user.model.UpdateHandlerProfileRequest;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.model.user.UserResponse;
import com.WorkOrder.model.user.UserProfile;

import java.util.Collection;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月09日 23:25
 * @description 用户目录边界。当前实现可替换为 MyBatis 数据库实现，不影响接口层
 */
public interface UserDirectoryService {
    /**
     * 按管理员查询条件分页获取用户列表。
     *
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @param keyword 账号、姓名、邮箱或手机号关键字
     * @param role 可选角色：0 普通用户、1 处理人、2 管理员
     * @param status 可选状态：0 禁用、1 启用
     * @return 按用户 ID 倒序排列的分页结果
     */
    PageResult<UserResponse> listUsers(int page,
                                       int pageSize,
                                       String keyword,
                                       Integer role,
                                       Integer status);

    /**
     * 创建普通用户或处理人账户资料。
     *
     * @param createUserDto 创建用户数据传输对象
     * @return 创建后的用户资料
     */
    UserResponse create(CreateUserDto createUserDto);

    /**
     * 由管理员部分更新指定用户。
     *
     * @param userId 目标用户主键
     * @param currentUserId 当前管理员用户主键
     * @param request 待更新字段
     * @return 更新后的用户资料
     */
    UserResponse updateUser(Long userId, Long currentUserId, AdminUpdateUserDto request);

    /**
     * 按主键查询用户资料。
     *
     * @param id 用户主键
     * @return 用户资料
     */
    UserProfile getById(Long id);

    /**
     * 按主键批量查询用户资料。
     *
     * @param ids 用户主键集合
     * @return 查询到的用户资料
     */
    List<UserProfile> getByIds(Collection<Long> ids);

    /**
     * 查询可供派单引擎选择的启用处理人。
     *
     * @return 处理人资料列表
     */
    //List<UserProfile> listHandlers();

    /**
     * 更新处理人的最大容量和技能标签。
     *
     * @param userId 处理人用户主键
     * @param request 待更新的处理人档案
     * @return 更新后的用户资料
     */
    //UserProfile updateHandlerProfile(Long userId, UpdateHandlerProfileRequest request);
}

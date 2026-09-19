package com.WorkOrder.user.service;

import com.WorkOrder.user.model.CreateUserRequest;
import com.WorkOrder.user.model.UpdateHandlerProfileRequest;
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
     * 创建普通用户或处理人账户资料。
     *
     * @param request 用户创建请求
     * @return 创建后的用户资料
     */
    //UserProfile create(CreateUserRequest request);

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

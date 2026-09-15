package com.WorkOrder.user.service.impl;

import com.WorkOrder.user.mapper.UsersMapper;
import com.WorkOrder.model.user.UserProfile;
import com.WorkOrder.user.model.Users;
import com.WorkOrder.user.service.UserDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用于接口联调的临时存储实现。
 * 接入 MySQL 后请新增 Mapper 实现并替换本类，不要将密码或会话状态放在这里。
 */
@Service
@RequiredArgsConstructor
public class InMemoryUserDirectoryService implements UserDirectoryService {
    private final UsersMapper usersMapper;

    private final AtomicLong sequence = new AtomicLong(1000);
    private final Map<Long, UserProfile> users = new ConcurrentHashMap<>();

    /**
     * 初始化本地联调所需的管理员和处理人示例数据。
     */
//    @PostConstruct
//    public void seedUsers() {
//        UserProfile admin = new UserProfile(1L, "admin", "系统管理员", null, null, null,
//                new LinkedHashSet<UserRole>(), true, null);
//        admin.getRoles().add(UserRole.ADMIN);
//        users.put(admin.getId(), admin);
//
//        UserProfile handler = new UserProfile(2L, "handler.demo", "演示处理人", null, null, null,
//                new LinkedHashSet<UserRole>(), true, new HandlerProfile(10, null));
//        handler.getRoles().add(UserRole.HANDLER);
//        users.put(handler.getId(), handler);
//    }

    /**
     * 创建用户并在没有指定角色时赋予普通用户角色。
     *
     * @param request 已通过控制器校验的创建请求
     * @return 新创建用户的副本
     */
//    @Override
//    public synchronized UserProfile create(CreateUserRequest request) {
//        boolean exists = users.values().stream()
//                .anyMatch(user -> user.getUsername().equalsIgnoreCase(request.getUsername()));
//        if (exists) {
//            throw new IllegalArgumentException("用户名已存在");
//        }
//        LinkedHashSet<UserRole> roles = request.getRoles() == null
//                ? new LinkedHashSet<UserRole>() : new LinkedHashSet<>(request.getRoles());
//        if (roles.isEmpty()) {
//            roles.add(UserRole.USER);
//        }
//        Long id = sequence.incrementAndGet();
//        UserProfile user = new UserProfile(id, request.getUsername(), request.getRealName(), request.getEmail(),
//                request.getPhone(), request.getDepartmentId(), roles, true, null);
//        if (roles.contains(UserRole.HANDLER)) {
//            user.setHandlerProfile(new HandlerProfile(10, null));
//        }
//        users.put(id, user);
//        return copyOf(user);
//    }

    /**
     * 查询用户并返回副本，防止调用方修改内存存储内的对象。
     *
     * @param id 用户主键
     * @return 用户资料副本
     */
    @Override
    public UserProfile getById(Long id) {
        Users user = usersMapper.selectById(id);

        UserProfile userProfile = new UserProfile();
        BeanUtils.copyProperties(user, userProfile);

        return userProfile;
    }

    /**
     * 仅返回启用且带有 HANDLER 角色的用户，供派单引擎使用。
     *
     * @return 处理人资料列表
     */
//    @Override
//    public List<UserProfile> listHandlers() {
//        return users.values().stream()
//                .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
//                .filter(user -> user.getRoles().contains(UserRole.HANDLER))
//                .map(this::copyOf)
//                .collect(Collectors.toList());
//    }

    /**
     * 更新处理人档案，非处理人角色不允许维护该档案。
     *
     * @param userId 用户主键
     * @param request 容量和技能更新请求
     * @return 更新后的用户资料副本
     */
//    @Override
//    public synchronized UserProfile updateHandlerProfile(Long userId, UpdateHandlerProfileRequest request) {
//        UserProfile user = requireUser(userId);
//        if (!user.getRoles().contains(UserRole.HANDLER)) {
//            throw new IllegalArgumentException("该用户不是处理人，不能维护处理人档案");
//        }
//        user.setHandlerProfile(new HandlerProfile(request.getMaxCapacity(), request.getSkills()));
//        return copyOf(user);
//    }

    /**
     * 读取用户；不存在时抛出异常供接口层转换为 404 响应。
     *
     * @param id 用户主键
     * @return 内存中的用户实体
     */
    private UserProfile requireUser(Long id) {
        UserProfile user = users.get(id);
        if (user == null) {
            throw new NoSuchElementException("用户不存在：" + id);
        }
        return user;
    }

    /**
     * 构造包含独立集合和档案对象的副本，避免内部状态被外部请求篡改。
     *
     * @param source 源用户资料
     * @return 用户资料副本
     */
//    private UserProfile copyOf(UserProfile source) {
//        HandlerProfile profile = source.getHandlerProfile() == null ? null
//                : new HandlerProfile(source.getHandlerProfile().getMaxCapacity(), source.getHandlerProfile().getSkills());
//        return new UserProfile(source.getId(), source.getUsername(), source.getRealName(), source.getEmail(),
//                source.getPhone(), source.getDepartmentId(), new LinkedHashSet<>(source.getRoles()),
//                source.getEnabled(), profile);
//    }
}

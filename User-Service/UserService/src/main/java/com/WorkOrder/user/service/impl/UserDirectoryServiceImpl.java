package com.WorkOrder.user.service.impl;

import com.WorkOrder.handler.mapper.HandlerProfileMapper;
import com.WorkOrder.handler.model.Department;
import com.WorkOrder.handler.model.HandlerProfiles;
import com.WorkOrder.model.handler.HandlerProfile;
import com.WorkOrder.user.dto.AdminUpdateUserDto;
import com.WorkOrder.user.dto.CreateUserDto;
import com.WorkOrder.user.mapper.DepartmentMapper;
import com.WorkOrder.user.mapper.HandlerSkillMapper;
import com.WorkOrder.user.mapper.UsersMapper;
import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.model.user.UserResponse;
import com.WorkOrder.model.user.UserProfile;
import com.WorkOrder.user.model.HandlerSkill;
import com.WorkOrder.user.model.Users;
import com.WorkOrder.user.service.UserDirectoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 用于接口联调的临时存储实现。
 * 接入 MySQL 后请新增 Mapper 实现并替换本类，不要将密码或会话状态放在这里。
 */
@Service
@RequiredArgsConstructor
public class UserDirectoryServiceImpl implements UserDirectoryService {
    private final UsersMapper usersMapper;
    private final HandlerProfileMapper handlerProfileMapper;
    private final DepartmentMapper departmentMapper;
    private final HandlerSkillMapper handlerSkillMapper;

    private final AtomicLong sequence = new AtomicLong(1000);
    private final Map<Long, UserProfile> users = new ConcurrentHashMap<>();

    private final PasswordEncoder passwordEncoder;

    /**
     * 分页查询用户。
     *
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @param keyword 账号、姓名、邮箱或手机号关键字
     * @param role 可选角色
     * @param status 可选状态
     * @return 用户分页结果
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<UserResponse> listUsers(int page,
                                              int pageSize,
                                              String keyword,
                                              Integer role,
                                              Integer status) {
        if (page < 1 || pageSize < 1
                || (role != null && (role < 0 || role > 2))
                || (status != null && (status < 0 || status > 1))) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }

        // 处理关键字
        String normalizedKeyword = keyword == null || keyword.trim().isEmpty()
                ? null : keyword.trim();
        // 执行分页查询
        Page<UserResponse> result = usersMapper.selectUserPage(
                new Page<>(page, pageSize), normalizedKeyword, role, status);
        // 处理查询结果
        List<UserResponse> records = result == null || result.getRecords() == null
                ? Collections.emptyList() : result.getRecords();
        long total = result == null ? 0L : result.getTotal();
        return new PageResult<>(records, total, page, pageSize);
    }

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
     * @param createUserDto 已通过控制器校验的创建请求
     * @return 新创建用户的副本
     */
    @Override
    @Transactional
    public UserResponse create(CreateUserDto createUserDto) {
        // 检查用户名是否已存在
        Users user = usersMapper.selectOne(new LambdaQueryWrapper<Users>()
                .eq(Users::getUsername, createUserDto.getUsername()));
        if (user != null && user.getUsername().equals(createUserDto.getUsername())) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_HAS_BEEN_CREATED);
        }

        Users newUser = new Users();
        newUser.setUsername(createUserDto.getUsername());
        newUser.setRealName(createUserDto.getRealName());
        newUser.setEmail(createUserDto.getEmail());
        newUser.setPhone(createUserDto.getPhone());
        if (createUserDto.getDepartmentId() != null){
            newUser.setDepartmentId(createUserDto.getDepartmentId());
        }
        newUser.setRole(createUserDto.getRole());
        newUser.setStatus(createUserDto.getStatus());
        newUser.setPassword(passwordEncoder.encode(createUserDto.getPassword()));
        int insert = usersMapper.insert(newUser);
        if (insert < 1) {
            throw new SystemException(SystemExceptionEnum.CREATE_FAILED);
        }


        // 获取新创建的用户
        Users createdUser = usersMapper.selectOne(new LambdaQueryWrapper<Users>()
                .eq(Users::getUsername, createUserDto.getUsername()));
        // 同步用户角色
        synchronizeUserRole(createdUser.getId(), createUserDto.getRole());
        if (createUserDto.getRole() == 1) {
            ensureHandlerProfile(createdUser.getId());
        }
        return toUserResponse(createdUser, new UserResponse());
    }

    /**
     * 按接口文档 14.3 部分更新用户，并维护角色关联及处理人档案。
     *
     * @param userId 目标用户主键
     * @param currentUserId 当前管理员用户主键
     * @param request 待更新字段
     * @return 更新后的用户管理员视图
     */
    @Override
    @Transactional
    public UserResponse updateUser(Long userId,
                                   Long currentUserId,
                                   AdminUpdateUserDto request) {
        Users existing = usersMapper.selectById(userId);
        if (existing == null) {
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }
        if (request.isStatusPresent()
                && Integer.valueOf(0).equals(request.getStatus())
                && Objects.equals(userId, currentUserId)) {
            throw new SystemException("不能禁用自己的账号");
        }
        validatePresentValues(request);

        LambdaUpdateWrapper<Users> update = new LambdaUpdateWrapper<>();
        update.eq(Users::getId, userId);
        boolean userChanged = false;

        if (request.isRealNamePresent()) {
            update.set(Users::getRealName, request.getRealName().trim());
            userChanged = true;
        }
        if (request.isEmailPresent()) {
            update.set(Users::getEmail, nullableText(request.getEmail()));
            userChanged = true;
        }
        if (request.isPhonePresent()) {
            update.set(Users::getPhone, nullableText(request.getPhone()));
            userChanged = true;
        }
        if (request.isDepartmentIdPresent()) {
            update.set(Users::getDepartmentId, request.getDepartmentId());
            userChanged = true;
        }
        if (request.isPasswordPresent() && StringUtils.hasText(request.getPassword())) {
            update.set(Users::getPassword, passwordEncoder.encode(request.getPassword()));
            userChanged = true;
        }
        if (request.isRolePresent()) {
            update.set(Users::getRole, request.getRole());
            userChanged = true;
        }
        if (request.isStatusPresent()) {
            update.set(Users::getStatus, request.getStatus());
            userChanged = true;
        }

        if (userChanged) {
            usersMapper.update(null, update);
        }

        if (request.isRolePresent()) {
            synchronizeUserRole(userId, request.getRole());
            if (request.getRole() == 1) {
                ensureHandlerProfile(userId);
            }
        }

        UserResponse updated = usersMapper.selectUserResponseById(userId);
        if (updated == null) {
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }
        return updated;
    }

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
     * 按主键批量查询用户资料。
     *
     * @param ids 用户主键集合
     * @return 查询到的用户资料
     */
    @Override
    public List<UserProfile> getByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        Collection<Long> distinctIds = ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinctIds.isEmpty()) {
            return Collections.emptyList();
        }

        return usersMapper.selectBatchIds(distinctIds).stream()
                .map(user -> {
                    UserProfile userProfile = new UserProfile();
                    BeanUtils.copyProperties(user, userProfile);
                    return userProfile;
                })
                .collect(Collectors.toList());
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

    /** 校验只有在 JSON 中显式出现时才要求非空的字段。 */
    private void validatePresentValues(AdminUpdateUserDto request) {
        if (request.isRealNamePresent() && !StringUtils.hasText(request.getRealName())) {
            throw new SystemException("姓名不能为空");
        }
        if (request.isRolePresent() && request.getRole() == null) {
            throw new SystemException("角色不能为空");
        }
        if (request.isStatusPresent() && request.getStatus() == null) {
            throw new SystemException("状态不能为空");
        }
    }

    /** 空白联系方式按清空字段处理，避免唯一索引中保存多个空字符串。 */
    private String nullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** 同步 users.role 对应的 RBAC 显式角色，保证权限查询与角色字段一致。 */
    private void synchronizeUserRole(Long userId, int role) {
        usersMapper.deleteUserRoles(userId);
        if (usersMapper.insertUserRole(userId, roleCode(role)) != 1) {
            throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
        }
    }

    /** 为首次成为处理人的用户创建默认档案；历史档案存在时直接复用。 */
    private void ensureHandlerProfile(Long userId) {
        Integer count = handlerProfileMapper.selectCount(
                new LambdaQueryWrapper<HandlerProfiles>()
                        .eq(HandlerProfiles::getUserId, userId));
        if (count != null && count > 0) {
            return;
        }

        HandlerProfiles handlerProfile = new HandlerProfiles();
        handlerProfile.setUserId(userId);
        handlerProfile.setMaxCapacity(10);
        handlerProfile.setCurrentLoad(0);
        handlerProfile.setAvgResponseMinutes(0);
        handlerProfile.setAvgResolutionMinutes(0);
        handlerProfile.setSlaComplianceRate(BigDecimal.ZERO);
        handlerProfile.setRatingScore(BigDecimal.ZERO);
        if (handlerProfileMapper.insert(handlerProfile) < 1) {
            throw new SystemException(SystemExceptionEnum.CREATE_FAILED);
        }
    }

    /** 将数值角色转换为 roles.code。 */
    private String roleCode(int role) {
        switch (role) {
            case 2:
                return "ADMIN";
            case 1:
                return "HANDLER";
            default:
                return "USER";
        }
    }

    /**
     * 将用户实体转换为用户响应对象。
     * @param user 用户实体
     * @param userResponse 用户响应对象
     * @return 用户响应对象
     */
    private UserResponse toUserResponse(Users user, UserResponse userResponse) {
        BeanUtils.copyProperties(user, userResponse);
        Department department = departmentMapper.selectById(user.getDepartmentId());
        userResponse.setDepartmentName(department.getName());
        return userResponse;
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

package com.WorkOrder.auth.service.impl;

import com.WorkOrder.auth.mapper.LoginMapper;
import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.user.model.Users;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 从业务数据库加载 OAuth2 password grant 所需的账号信息。 */
@Service
public class LoginUserDetailsService implements UserDetailsService {
    private final LoginMapper loginMapper;

    /**
     * 创建数据库账号查询服务。
     *
     * @param loginMapper 登录与权限数据访问接口
     */
    public LoginUserDetailsService(LoginMapper loginMapper) {
        this.loginMapper = loginMapper;
    }

    /**
     * 加载密码校验、账号状态校验和 JWT 权限声明所需的用户信息。
     *
     * @param username 登录账号
     * @return Spring Security 用户主体
     * @throws UsernameNotFoundException 账号不存在时抛出
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Users account = loginMapper.findByUsername(username);
        if (account == null) {
            // 不暴露账号是否存在，最终统一转换成“用户名或密码错误”。
            throw new UsernameNotFoundException(SystemExceptionEnum.INVALID_CREDENTIALS.getErrMessage());
        }

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + roleCode(account.getRole())));

        List<String> permissions = loginMapper.findPermissionCodes(account.getId(), account.getRole());
        for (String permission : permissions == null ? Collections.<String>emptyList() : permissions) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }

        return User.withUsername(account.getUsername())
                .password(account.getPassword())
                .authorities(authorities)
                .disabled(account.getStatus() != 1)
                .build();
    }

    /**
     * 将 users.role 中的数值角色映射为 Spring Security 角色编码。
     *
     * @param role 数值角色：0 普通用户、1 处理人、2 管理员
     * @return USER、HANDLER 或 ADMIN
     */
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
}

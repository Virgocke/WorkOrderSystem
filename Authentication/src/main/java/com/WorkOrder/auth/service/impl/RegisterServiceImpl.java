package com.WorkOrder.auth.service.impl;

import com.WorkOrder.auth.dto.ResetPasswordRequestDto;
import com.WorkOrder.auth.mapper.UsersMapper;
import com.WorkOrder.auth.dto.RegisterUserDto;
import com.WorkOrder.auth.model.AuthenticatedUser;
import com.WorkOrder.auth.dto.AuthenticatedUserDto;
import com.WorkOrder.auth.service.AuthenticationService;
import com.WorkOrder.auth.service.RegisterService;
import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.UserResponse;
import com.WorkOrder.user.model.Users;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.OAuth2Request;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Virgor
 * @date 2026年09月11日 02:29
 * @description 注册服务实现类
 */
@Service
public class RegisterServiceImpl implements RegisterService {

    @Autowired
    private UsersMapper usersMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    @Qualifier("authenticationManagerBean")
    private AuthenticationManager authenticationManager;

    @Autowired
    @Qualifier("authorizationServerTokenServices")
    private AuthorizationServerTokenServices tokenServices;

    @Autowired
    private AuthenticationService authenticationService;

    @Value("${security.oauth2.client.id}")
    private String clientId;

    private static final String CODE_PREFIX = "email:code:";

    /**
     * 注册功能
     * @param registerUserDto
     * @return UserResponse
     */
    @Override
    public UserResponse register(RegisterUserDto registerUserDto) {
        // 查询用户名是否已存在
        Users user = usersMapper.selectOne(new LambdaQueryWrapper<Users>().eq(Users::getUsername, registerUserDto.getUsername()));
        if (user != null){
            throw new SystemException(SystemExceptionEnum.ACCOUNT_HAS_BEEN_CREATED);
        } else {
            user = new Users();
        }
        // 创建返回的用户对象
        BeanUtils.copyProperties(registerUserDto, user);
        //userResponse.setCreatedAt(String.valueOf(new Date()));
        user.setRole(0);
        user.setStatus(1); //启用

        // 创建用户对象存入数据库
        String password = registerUserDto.getPassword(); // 获取密码
        String encodedPassword = passwordEncoder.encode(password);
        user.setPassword(encodedPassword);
        int insert = usersMapper.insert(user);
        if (insert != 1) {
            throw new SystemException(SystemExceptionEnum.REGISTER_FAILED);
        }
        Users savedUser = usersMapper.selectOne(new LambdaQueryWrapper<Users>().eq(Users::getUsername, user.getUsername()));

        UserResponse userResponse = new UserResponse();
        BeanUtils.copyProperties(savedUser, userResponse);
        return userResponse;
    }

    /**
     * 注册后在服务端立即签发登录态，前端无需再额外请求 OAuth2 token 端点。
     *
     * @param registerUserDto 已通过参数校验的注册信息
     * @return 访问令牌、用户资料与权限
     */
    @Override
    public AuthenticatedUserDto registerAndLogin(RegisterUserDto registerUserDto) {
        register(registerUserDto);
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(registerUserDto.getUsername(), registerUserDto.getPassword()));
        OAuth2AccessToken accessToken = tokenServices.createAccessToken(createOAuth2Authentication(authentication));
        AuthenticatedUser profile = authenticationService.toCurrentUser(authentication);
        return new AuthenticatedUserDto(
                accessToken.getValue(),
                profile,
                new ArrayList<>(profile.getPermissions()));
    }

    /**
     * 重置密码
     * @param request
     * @return 成功则返回true
     */
    @Override
    public Boolean resetPassword(ResetPasswordRequestDto request) {
        // 判断第二次邮箱是否正确
        Users user = usersMapper.selectByEmail(request.getEmail());
        if (user == null){
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }

        // 验证邮箱验证码是否正确
        String code = redisTemplate.opsForValue().get(CODE_PREFIX + request.getEmail());
        if(!request.getCode().equals(code)){
            throw new SystemException(SystemExceptionEnum.CODE_ERROR);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        int update = usersMapper.updateById(user);
        if (update != 1) {
            throw new SystemException("重置密码失败");
        }
        return true;
    }

    /** 为刚注册的用户建立与 password grant 一致的 OAuth2 认证上下文。 */
    private OAuth2Authentication createOAuth2Authentication(Authentication userAuthentication) {
        Map<String, String> requestParameters = new HashMap<>();
        requestParameters.put("client_id", clientId);
        requestParameters.put("grant_type", "password");
        OAuth2Request request = new OAuth2Request(
                requestParameters,
                clientId,
                userAuthentication.getAuthorities(),
                true,
                Collections.singleton("work-order"),
                null,
                null,
                null,
                null);
        return new OAuth2Authentication(request, userAuthentication);
    }
}

package com.WorkOrder.auth.service.impl;

import com.WorkOrder.auth.dto.ResetPasswordRequestDto;
import com.WorkOrder.auth.mapper.UsersMapper;
import com.WorkOrder.auth.dto.RegisterUserDto;
import com.WorkOrder.auth.service.RegisterService;
import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.UserResponse;
import com.WorkOrder.user.model.Users;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
}

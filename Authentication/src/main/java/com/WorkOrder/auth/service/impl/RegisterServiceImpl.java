package com.WorkOrder.auth.service.impl;

import com.WorkOrder.auth.mapper.UsersMapper;
import com.WorkOrder.auth.model.RegisterUser;
import com.WorkOrder.auth.service.RegisterService;
import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.UserResponse;
import com.WorkOrder.user.model.Users;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;

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

    /**
     * 注册功能
     * @param registerUser
     * @return UserResponse
     */
    @Override
    public UserResponse register(RegisterUser registerUser) {
        // 查询用户名是否已存在
        Users user = usersMapper.selectOne(new LambdaQueryWrapper<Users>().eq(Users::getUsername, registerUser.getUsername()));
        if (user != null){
            throw new SystemException(SystemExceptionEnum.ACCOUNT_HAS_BEEN_CREATED);
        } else {
            user = new Users();
        }
        // 创建返回的用户对象
        BeanUtils.copyProperties(registerUser, user);
        //userResponse.setCreatedAt(String.valueOf(new Date()));
        user.setRole(0);
        user.setStatus(1); //启用

        // 创建用户对象存入数据库
        String password = registerUser.getPassword(); // 获取密码
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
}

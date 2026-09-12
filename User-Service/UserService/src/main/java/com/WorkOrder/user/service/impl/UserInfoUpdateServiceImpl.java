package com.WorkOrder.user.service.impl;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.UserResponse;
import com.WorkOrder.user.dto.PasswordDto;
import com.WorkOrder.user.dto.UserUpdateDto;
import com.WorkOrder.user.mapper.UsersMapper;
import com.WorkOrder.user.model.Users;
import com.WorkOrder.user.service.UserInfoUpdateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Virgor
 * @date 2026年09月12日 21:02
 * @description 用户信息更新服务实现类
 */
@Service
@RequiredArgsConstructor
public class UserInfoUpdateServiceImpl implements UserInfoUpdateService {

    private final UsersMapper usersMapper;

    private final PasswordEncoder passwordEncoder;


    @Override
    @Transactional
    public UserResponse updateUserInfo(UserUpdateDto userUpdateDto) {
        Users user = usersMapper.selectOne(new LambdaQueryWrapper<Users>().eq(Users::getPhone, userUpdateDto.getPhone()));
        if(user == null){
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }
        user.setPhone(userUpdateDto.getPhone());
        user.setRealName(userUpdateDto.getRealName());
        user.setEmail(userUpdateDto.getEmail());
        int result = usersMapper.updateById(user);
        if(result == 0){
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        UserResponse userResponse = new UserResponse();
        BeanUtils.copyProperties(user, userResponse);
        return userResponse;
    }

    @Override
    public Boolean updateUserPassword(String username, PasswordDto passwordDto) {
        Users user = usersMapper.selectOne(new LambdaQueryWrapper<Users>().eq(Users::getUsername, username));
        if (user == null) {
            throw new SystemException(SystemExceptionEnum.ABNORMAL_ACCOUNT);
        }
        if (!passwordEncoder.matches(passwordDto.getOldPassword(), user.getPassword())) {
            throw new SystemException("旧密码错误");
        }
        user.setPassword(passwordEncoder.encode(passwordDto.getNewPassword()));
        int result = usersMapper.updateById(user);
        if (result == 0) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        return true;
    }
}

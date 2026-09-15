package com.WorkOrder.handler.service.impl;

import com.WorkOrder.handler.mapper.HandlerProfileMapper;
import com.WorkOrder.handler.model.HandlerProfiles;
import com.WorkOrder.handler.service.HandlerUserService;
import com.WorkOrder.model.handler.HandlerProfile;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Virgor
 * @date 2026年09月16日 02:41
 * @description 处理人服务实现类（管理层）
 */
@Service
@RequiredArgsConstructor
public class HandlerUserServiceImpl extends ServiceImpl<HandlerProfileMapper, HandlerProfiles> implements HandlerUserService {

    private final HandlerProfileMapper handlerProfileMapper;

    /**
     * 获取处理人信息
     * @param userId 用户ID
     * @param username 用户名
     * @return 处理人信息
     */
    @Override
    public List<HandlerProfile> getHandler(Long userId, String username) {
        List<HandlerProfiles> handler = handlerProfileMapper.selectList(null);

        List<HandlerProfile> collected = handler.stream()
                .map(handler1 ->
                        buildHandlerProfile(handler1, new HandlerProfile(), userId, username))
                .collect(Collectors.toList());

        return collected;
    }

    /**
     * 构建处理人信息
     * @param handlerProfiles 处理人信息
     * @param handlerProfile 处理人信息
     * @param userId 用户ID
     * @param username 用户名
     * @return 处理人信息
     */
    private HandlerProfile buildHandlerProfile(
            HandlerProfiles handlerProfiles,
            HandlerProfile handlerProfile,
            Long userId,
            String username){

        BeanUtils.copyProperties(handlerProfiles, handlerProfile);

        handlerProfile.setUserId(userId);
        handlerProfile.setUsername(username);
        handlerProfile.setDepartmentId(null);
        handlerProfile.setDepartmentName(null);
        handlerProfile.setStatus(1);

        //todo 处理人技能还未填入

        return handlerProfile;
    }
}

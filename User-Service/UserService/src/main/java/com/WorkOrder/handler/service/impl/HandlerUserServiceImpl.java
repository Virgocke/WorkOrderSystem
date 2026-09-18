package com.WorkOrder.handler.service.impl;

import com.WorkOrder.handler.mapper.HandlerProfileMapper;
import com.WorkOrder.handler.model.HandlerProfiles;
import com.WorkOrder.handler.service.HandlerUserService;
import com.WorkOrder.model.handler.HandlerProfile;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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
     * 查询启用处理人的真实用户资料、部门与档案信息。
     * @return 可用于分配的处理人列表
     */
    @Override
    public List<HandlerProfile> getHandler() {
        return handlerProfileMapper.selectEnabledHandlerOptions();
    }
}

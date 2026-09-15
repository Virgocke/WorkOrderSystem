package com.WorkOrder.handler.service;

import com.WorkOrder.handler.model.HandlerProfiles;
import com.WorkOrder.model.handler.HandlerProfile;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月16日 02:40
 * @description
 */

public interface HandlerUserService extends IService<HandlerProfiles> {

    /**
     * 获取处理人列表
     * @return 处理人列表
     */
    List<HandlerProfile> getHandler(Long userId, String username);
}

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
     * 获取启用处理人的分配选项，响应中的 id 和 userId 均为处理人用户 ID。
     * @return 启用处理人列表
     */
    List<HandlerProfile> getHandler();
}

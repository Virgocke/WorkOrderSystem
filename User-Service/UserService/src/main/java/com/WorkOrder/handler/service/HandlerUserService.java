package com.WorkOrder.handler.service;

import com.WorkOrder.handler.dto.HandlerPageListDto;
import com.WorkOrder.handler.dto.UpdateHandlerProfileRequest;
import com.WorkOrder.handler.dto.UpdateHandlerSkillsRequest;
import com.WorkOrder.handler.model.HandlerProfiles;
import com.WorkOrder.model.handler.HandlerProfile;
import com.WorkOrder.model.page.PageResult;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月16日 02:40
 * @description 查询处理人账号与处理人档案。
 */

public interface HandlerUserService extends IService<HandlerProfiles> {

    /**
     * 获取启用处理人的分配选项，响应中的 id 和 userId 均为处理人用户 ID。
     *
     * @return 启用处理人列表
     */
    List<HandlerProfile> getHandler();

    /**
     * 分页查询处理人档案。
     *
     * @param query 分页与筛选条件
     * @return 处理人分页结果
     */
    PageResult<HandlerProfile> listHandlers(HandlerPageListDto query);

    /**
     * 查询全部处理人档案，包含停用账号。
     *
     * @return 全部处理人档案
     */
    List<HandlerProfile> listAllHandlers();

    /**
     * 按用户 ID 部分更新处理人容量、所属部门和账号状态。
     *
     * @param userId 处理人用户 ID
     * @param request 待更新字段
     * @return 更新后的完整处理人档案
     */
    HandlerProfile updateHandler(Long userId, UpdateHandlerProfileRequest request);

    /**
     * 按用户 ID 全量覆盖处理人的技能。
     *
     * @param userId 处理人用户 ID
     * @param request 完整技能列表，空列表表示清空
     * @return 更新后的完整处理人档案
     */
    HandlerProfile updateHandlerSkills(Long userId, UpdateHandlerSkillsRequest request);

    /**
     * 查询当前处理人的完整档案及技能。
     *
     * @param userId Token 中的当前用户 ID
     * @return 当前处理人档案
     */
    HandlerProfile getMyHandlerProfile(Long userId);
}

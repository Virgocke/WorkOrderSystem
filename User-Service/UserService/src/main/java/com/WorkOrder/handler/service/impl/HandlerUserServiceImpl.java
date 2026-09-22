package com.WorkOrder.handler.service.impl;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.handler.dto.HandlerPageListDto;
import com.WorkOrder.handler.dto.UpdateHandlerProfileRequest;
import com.WorkOrder.handler.mapper.HandlerProfileMapper;
import com.WorkOrder.handler.model.HandlerProfiles;
import com.WorkOrder.handler.service.HandlerUserService;
import com.WorkOrder.model.handler.HandlerProfile;
import com.WorkOrder.model.page.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
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

    /**
     * 分页查询处理人列表
     * @param query 分页与筛选条件
     * @return 处理人列表
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<HandlerProfile> listHandlers(HandlerPageListDto query) {
        if (query == null || query.getPage() == null || query.getPageSize() == null
                || query.getPage() < 1 || query.getPageSize() < 1
                || (query.getStatus() != null
                && (query.getStatus() < 0 || query.getStatus() > 1))) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }

        String keyword = query.getKeyword() == null || query.getKeyword().trim().isEmpty()
                ? null : query.getKeyword().trim();
        // 执行分页查询
        Page<HandlerProfile> result = handlerProfileMapper.selectHandlerPage(
                new Page<>(query.getPage(), query.getPageSize()), keyword, query.getStatus());
        List<HandlerProfile> records = result == null || result.getRecords() == null
                ? Collections.emptyList() : result.getRecords();
        long total = result == null ? 0L : result.getTotal();
        return new PageResult<>(records, total, query.getPage(), query.getPageSize());
    }

    /**
     * 查询所有处理人列表
     * @return 处理人列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<HandlerProfile> listAllHandlers() {
        List<HandlerProfile> handlers = handlerProfileMapper.selectAllHandlerProfiles();
        return handlers == null ? Collections.emptyList() : handlers;
    }

    /**
     * 按接口文档 15.4 部分更新处理人档案。档案容量与用户部门、状态分属两张表，
     * 因此必须在同一事务中完成。
     */
    @Override
    @Transactional
    public HandlerProfile updateHandler(Long userId, UpdateHandlerProfileRequest request) {
        validateUpdateRequest(userId, request);

        HandlerProfile existing = handlerProfileMapper.selectHandlerProfileByUserId(userId);
        if (existing == null) {
            throw new SystemException(SystemExceptionEnum.RESOURCE_NOT_FOUND);
        }

        if (request.isDepartmentIdPresent() && request.getDepartmentId() != null) {
            if (request.getDepartmentId() <= 0) {
                throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
            }
            if (handlerProfileMapper.countDepartmentById(request.getDepartmentId()) == 0) {
                throw new SystemException(SystemExceptionEnum.RESOURCE_NOT_FOUND);
            }
        }

        if (request.isMaxCapacityPresent()) {
            int affected = handlerProfileMapper.updateHandlerCapacity(userId, request.getMaxCapacity());
            if (affected > 1) {
                throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
            }
        }
        // 更新处理人部门与状态
        if (request.isDepartmentIdPresent() || request.isStatusPresent()) {
            int affected = handlerProfileMapper.updateHandlerUser(
                    userId,
                    request.getDepartmentId(),
                    request.isDepartmentIdPresent(),
                    request.getStatus(),
                    request.isStatusPresent());
            if (affected > 1) {
                throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
            }
        }

        HandlerProfile updated = handlerProfileMapper.selectHandlerProfileByUserId(userId);
        if (updated == null) {
            throw new SystemException(SystemExceptionEnum.RESOURCE_NOT_FOUND);
        }
        return updated;
    }

    /** 服务被非 Web 调用时仍执行与 Bean Validation 一致的边界校验。 */
    private void validateUpdateRequest(Long userId, UpdateHandlerProfileRequest request) {
        if (userId == null || userId <= 0 || request == null) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        if (request.isMaxCapacityPresent()
                && (request.getMaxCapacity() == null
                || request.getMaxCapacity() < 1
                || request.getMaxCapacity() > 1000)) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        if (request.isStatusPresent()
                && (request.getStatus() == null
                || (request.getStatus() != 0 && request.getStatus() != 1))) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
    }
}

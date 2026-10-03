package com.WorkOrder.user.service;

import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.Result;
import com.WorkOrder.user.feignclient.DirectorySearchFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** 统一校验搜索响应，避免搜索故障或非法 ID 被误当成无条件查询。 */
@Service
@RequiredArgsConstructor
public class DirectoryKeywordSearch {
    private final DirectorySearchFeignClient client;

    /** 返回去重后的候选用户 ID；无匹配是空集合，异常明确失败。 */
    public List<Long> findUserIds(String keyword, boolean handlers) {
        Result<List<Long>> result;
        try {
            result = handlers ? client.searchHandlers(keyword) : client.searchUsers(keyword);
        } catch (RuntimeException exception) {
            throw new SystemException("用户搜索服务暂不可用，请稍后重试");
        }
        if (result == null || result.getCode() != 0 || result.getData() == null
                || result.getData().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new SystemException("用户搜索服务返回了无效结果");
        }
        return new ArrayList<>(new LinkedHashSet<>(result.getData()));
    }
}

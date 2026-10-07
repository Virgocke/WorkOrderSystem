package com.WorkOrder.ticket.support;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.user.UserProfile;
import com.WorkOrder.ticket.feignclient.UserFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 统一解析工单操作人显示名称，避免时间线和操作日志分别调用用户服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketActorNameResolver {

    private static final String SYSTEM_NAME = "系统";
    private static final String UNKNOWN_NAME = "未知用户";

    private final UserFeignClient userFeignClient;

    /**
     * 批量解析操作人姓名。
     *
     * @param operatorIds 操作人ID集合，允许包含 null
     * @return 操作人ID到显示名称的映射；null 对应系统
     */
    public Map<Long, String> resolveNames(Collection<Long> operatorIds) {
        Map<Long, String> names = new HashMap<>();
        names.put(null, SYSTEM_NAME);

        if (operatorIds == null || operatorIds.isEmpty()) {
            return names;
        }

        Set<Long> distinctIds = operatorIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinctIds.isEmpty()) {
            return names;
        }

        try {
            Result<List<UserProfile>> result = userFeignClient.getByIds(distinctIds);
            if (result != null && result.getData() != null) {
                for (UserProfile user : result.getData()) {
                    if (user != null && user.getId() != null) {
                        names.put(user.getId(), displayName(user));
                    }
                }
            }
        } catch (RuntimeException exception) {
            log.warn("批量查询操作人姓名失败，operatorIds={}", distinctIds, exception);
        }

        distinctIds.forEach(id -> names.putIfAbsent(id, UNKNOWN_NAME));
        return names;
    }

    /**
     * 优先使用真实姓名，缺失时退回登录账号。
     *
     * @param user 用户资料
     * @return 显示名称
     */
    private String displayName(UserProfile user) {
        if (user.getRealName() != null && !user.getRealName().trim().isEmpty()) {
            return user.getRealName().trim();
        }
        if (user.getUsername() != null && !user.getUsername().trim().isEmpty()) {
            return user.getUsername().trim();
        }
        return UNKNOWN_NAME;
    }
}

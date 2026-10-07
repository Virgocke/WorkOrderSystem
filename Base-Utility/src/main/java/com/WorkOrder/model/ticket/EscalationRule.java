package com.WorkOrder.model.ticket;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 自动升级规则快照；配置保存、SLA 扫描和工单执行采用同一校验与计时语义。
 */
@Getter
public final class EscalationRule {
    /**
     * 升级目标级别，连续的 1–3 级。
     */
    private final int level;
    /**
     * 响应截止后等待的整数分钟数，10–10080。
     */
    private final int responseTimeoutMin;
    /**
     * 解决截止后等待的整数分钟数，30–10080。
     */
    private final int resolutionTimeoutMin;
    /**
     * 接收对象：MANAGER 为部门负责人，ADMIN 为启用的管理员。
     */
    private final String targetRole;

    /**
     * 保存已经校验的规则。
     *
     * @param level 升级目标级别，连续的 1–3 级
     * @param responseTimeoutMin 响应截止后等待的整数分钟数，10–10080
     * @param resolutionTimeoutMin 解决截止后等待的整数分钟数，30–10080
     * @param targetRole 接收对象：MANAGER 为部门负责人，ADMIN 为启用的管理员
     */
    private EscalationRule(int level, int responseTimeoutMin, int resolutionTimeoutMin, String targetRole) {
        this.level = level;
        this.responseTimeoutMin = responseTimeoutMin;
        this.resolutionTimeoutMin = resolutionTimeoutMin;
        this.targetRole = targetRole;
    }

    /**
     * 校验完整有序规则数组；空数组停用自动升级，说明 desc 为可选的至多 200 字符文本。
     *
     * @param value 待处理的值
     * @return Escalation规则列表
     */
    public static List<EscalationRule> fromJson(JsonNode value) {
        if (value == null || !value.isArray() || value.size() > 3) {
            throw new IllegalArgumentException("升级规则必须为最多3项的数组");
        }
        List<EscalationRule> rules = new ArrayList<>();
        int response = 0;
        int resolution = 0;
        for (JsonNode node : value) {
            if (!node.isObject()) {
                throw new IllegalArgumentException("升级规则必须为对象");
            }
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                if (!Arrays.asList("level", "responseTimeoutMin", "resolutionTimeoutMin", "targetRole", "desc")
                        .contains(names.next())) {
                    throw new IllegalArgumentException("升级规则含有不支持的字段");
                }
            }
            int level = integer(node, "level", 1, 3);
            int nextResponse = integer(node, "responseTimeoutMin", 10, 10080);
            int nextResolution = integer(node, "resolutionTimeoutMin", 30, 10080);
            JsonNode role = node.get("targetRole");
            if (role == null || !role.isTextual()
                    || !("MANAGER".equals(role.textValue()) || "ADMIN".equals(role.textValue()))) {
                throw new IllegalArgumentException("升级通知对象只能为MANAGER或ADMIN");
            }
            if (level != rules.size() + 1 || nextResponse <= response || nextResolution <= resolution) {
                throw new IllegalArgumentException("升级级别必须从1连续递增，两类超时分钟数必须分别严格递增");
            }
            JsonNode desc = node.get("desc");
            if (desc != null && (!desc.isTextual() || desc.textValue().length() > 200)) {
                throw new IllegalArgumentException("升级规则说明必须为不超过200字符的文本");
            }
            rules.add(new EscalationRule(level, nextResponse, nextResolution, role.textValue()));
            response = nextResponse;
            resolution = nextResolution;
        }
        return Collections.unmodifiableList(rules);
    }

    /**
     * 从整数 JSON 字段读取分钟数或级别，拒绝字符串、浮点和溢出。
     *
     * @param node 包含升级规则字段的 JSON 对象
     * @param field 待读取或校验的字段名
     * @param min 允许的整数下界，包含该值
     * @param max 允许的整数上界，包含该值
     * @return 通过类型和范围校验的整数值
     */
    private static int integer(JsonNode node, String field, int min, int max) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()
                || value.intValue() < min || value.intValue() > max) {
            throw new IllegalArgumentException(field + "必须为" + min + "至" + max + "的整数");
        }
        return value.intValue();
    }

    /**
     * 任一适用的截止时间达到阈值即匹配；已响应工单不再按响应截止时间升级。
     *
     * @param status 状态筛选或更新值
     * @param firstResponseAt first响应时间
     * @param responseDeadline SLA 响应截止时间
     * @param resolutionDeadline SLA 解决截止时间
     * @param now 当前处理时间
     * @return 操作是否成功
     */
    public boolean matches(String status, LocalDateTime firstResponseAt, LocalDateTime responseDeadline,
                           LocalDateTime resolutionDeadline, LocalDateTime now) {
        if (!Arrays.asList("PENDING_ASSIGN", "PENDING_RESPONSE", "PROCESSING").contains(status)) {
            return false;
        }
        return (firstResponseAt == null && !"PROCESSING".equals(status) && responseDeadline != null
                && !now.isBefore(responseDeadline.plusMinutes(responseTimeoutMin)))
                || (resolutionDeadline != null && !now.isBefore(resolutionDeadline.plusMinutes(resolutionTimeoutMin)));
    }
}

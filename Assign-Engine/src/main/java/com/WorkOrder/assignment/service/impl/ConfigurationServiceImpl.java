package com.WorkOrder.assignment.service.impl;

import com.WorkOrder.assignment.config.AssignWeightsProperties;
import com.WorkOrder.assignment.dto.SaveConfigurationsDto;
import com.WorkOrder.assignment.mapper.ConfigurationMapper;
import com.WorkOrder.assignment.model.Configuration;
import com.WorkOrder.assignment.model.ConfigurationItem;
import com.WorkOrder.assignment.service.ConfigurationService;
import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.assignment.AssignmentWeightsSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 系统配置保存在数据库中；每次推荐只读取一次，不使用本地缓存。 */
@Service
@RequiredArgsConstructor
public class ConfigurationServiceImpl implements ConfigurationService {
    private static final String WEIGHTS_KEY = "assignWeights";
    private static final List<String> WEIGHT_FIELDS = Arrays.asList("skill", "load", "sla", "rating");
    private static final BigDecimal MAX_WEIGHT = new BigDecimal("99999999.99");
    private final ConfigurationMapper configurationMapper;
    private final AssignWeightsProperties defaults;
    private final ObjectMapper objectMapper;

    /**
     * 列出所有配置项。
     * @param operatorRole 操作员角色
     * @return 配置项列表
     */
    @Override
    public List<ConfigurationItem> list(String operatorRole) {
        // 要求管理员权限
        requireAdministrator(operatorRole);

        // 获取所有配置项，包括默认值
        Map<String, ConfigurationItem> items = defaultItems();
        for (Configuration row : configurationMapper.selectAll()) {
            if (items.containsKey(row.getConfigKey())) {
                JsonNode value = parse(row.getConfigValue());

                if (WEIGHTS_KEY.equals(row.getConfigKey())) {
                    storedWeights(value, row.getVersion());
                }

                items.put(row.getConfigKey(),
                        new ConfigurationItem(
                                row.getConfigKey(),
                                value,
                                row.getDescription(),
                                row.getVersion(),
                                row.getUpdatedBy(),
                                row.getUpdatedAt()));
            }
        }
        return new ArrayList<>(items.values());
    }

    /** 整批校验后保存，版本冲突或日志失败时回滚整批修改。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(SaveConfigurationsDto dto, Long operatorId, String operatorRole) {

        requireAdministrator(operatorRole);

        // 校验参数，Id不能为空，角色不能为空，数据类不能为空，配置项不能为空，配置项数量不能大于5
        if (operatorId == null || operatorId <= 0 || dto == null || dto.getItems() == null
                || dto.getItems().isEmpty() || dto.getItems().size() > 5) {
            throw new IllegalArgumentException("配置保存参数不合法");
        }

        // 获取默认配置项
        Map<String, ConfigurationItem> initial = defaultItems();
        // 用于存储配置项的键
        Set<String> keys = new HashSet<>();

        for (SaveConfigurationsDto.Item item : dto.getItems()) {
            if (item == null || !initial.containsKey(item.getConfigKey())) {
                throw new IllegalArgumentException("不支持的配置键");
            }
            if (!keys.add(item.getConfigKey())) {
                throw new IllegalArgumentException("配置键不能重复");
            }
            if (item.getVersion() == null || item.getVersion() < 0
                    || item.getVersion() == Long.MAX_VALUE) {
                throw new IllegalArgumentException("配置版本不合法");
            }
            if (item.getValue() == null || item.getValue().isNull()) {
                throw new IllegalArgumentException("配置值不能为空");
            }
            // 校验权重值结构
            if (WEIGHTS_KEY.equals(item.getConfigKey())) {
                weightsSnapshot(item.getValue(), item.getVersion());
            } else if ("escalationRules".equals(item.getConfigKey())
                    ? !item.getValue().isArray() : !item.getValue().isObject()) {
                throw new IllegalArgumentException("配置值结构不合法");
            }
        }
        // 固定更新顺序，避免并发批量保存以不同顺序加锁。
        List<SaveConfigurationsDto.Item> ordered = dto.getItems().stream()
                .sorted(Comparator.comparing(SaveConfigurationsDto.Item::getConfigKey))
                .collect(Collectors.toList());
        // 遍历配置项，保存或更新
        for (SaveConfigurationsDto.Item item : ordered) {
            Configuration before = configurationMapper.selectByKey(item.getConfigKey());
            Configuration row = new Configuration();

            row.setConfigKey(item.getConfigKey());
            row.setConfigValue(item.getValue().toString());
            row.setDescription(initial.get(item.getConfigKey()).getDescription());
            row.setVersion(item.getVersion());
            row.setUpdatedBy(operatorId);

            if (before == null) {
                if (item.getVersion() != 0) {
                    throw new SystemException(SystemExceptionEnum.CONFIGURATION_VERSION_CONFLICT);
                }
                try {
                    if (configurationMapper.insert(row) != 1) {
                        throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
                    }
                } catch (DuplicateKeyException exception) {
                    throw new SystemException(SystemExceptionEnum.CONFIGURATION_VERSION_CONFLICT);
                }
            } else if (!item.getVersion().equals(before.getVersion())
                    || configurationMapper.updateByVersion(row) != 1) {
                throw new SystemException(SystemExceptionEnum.CONFIGURATION_VERSION_CONFLICT);
            }
            // 插入日志，记录配置项的修改历史
            String beforeValue = before == null ? initial.get(item.getConfigKey()).getValue().toString()
                    : before.getConfigValue();
            if (configurationMapper.insertLog(item.getConfigKey(), beforeValue, row.getConfigValue(),
                    item.getVersion() + 1, operatorId) != 1) {
                throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
            }
        }
        //todo 接入编号规则、SLA默认值、通知渠道和升级规则的业务消费，目前仅分配权重实时生效。
        //todo 将configuration_change_logs接入审计查询页面，目前已持久化修改记录。
        return true;
    }

    /** 只有记录不存在时使用默认值，数据库失败及非法存量配置不静默降级。 */
    @Override
    public AssignmentWeightsSnapshot getCurrentWeights() {
        Configuration row = configurationMapper.selectByKey(WEIGHTS_KEY);
        return row == null ? defaultWeights() : storedWeights(parse(row.getConfigValue()), row.getVersion());
    }

    /**
     * 配置项保存时使用，数据库中的非法存量数据不会被静默降级。
     * @param value 权重值
     * @param version 版本号
     * @return 权重快照
     */
    private AssignmentWeightsSnapshot storedWeights(JsonNode value, Long version) {
        try {
            return weightsSnapshot(value, version);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("数据库中的分配权重不合法", exception);
        }
    }

    /**
     * 配置项持久化时使用，数据库中的非法存量数据不会被静默降级。
     * @return 默认权重快照
     */
    private AssignmentWeightsSnapshot defaultWeights() {
        return new AssignmentWeightsSnapshot(defaults.getSkill(), defaults.getLoad(),
                defaults.getSla(), defaults.getRating(), 0L);
    }

    /** 配置缺失时返回页面原有默认值，版本0表示尚未持久化。 */
    private Map<String, ConfigurationItem> defaultItems() {
        Map<String, ConfigurationItem> items = new LinkedHashMap<>();
        // 工单编号规则配置
        items.put("ticketNoRule", defaultItem("ticketNoRule",
                parse("{\"prefix\":\"WO\",\"dateFormat\":\"YYYYMMDD\",\"seqLength\":4}"), "工单编号规则"));
        // SLA默认值配置
        items.put("slaDefaults", defaultItem("slaDefaults",
                parse("{\"responseMin\":30,\"resolutionMin\":240}"), "SLA默认值"));

        AssignmentWeightsSnapshot weights = defaultWeights();
        ObjectNode value = objectMapper.createObjectNode();
        // 智能分配评分权重配置
        value.put("skill", weights.getSkill())
                .put("load", weights.getLoad())
                .put("sla", weights.getSla())
                .put("rating", weights.getRating());
        // 通知渠道开关配置
        items.put(WEIGHTS_KEY, defaultItem(WEIGHTS_KEY, value, "智能分配评分权重"));
        // 升级规则配置
        items.put("notificationChannels", defaultItem("notificationChannels",
                parse("{\"internal\":true,\"email\":true,\"sms\":false}"), "通知渠道开关"));

        items.put("escalationRules", defaultItem("escalationRules", objectMapper.createArrayNode(), "升级规则配置"));
        return items;
    }

    /**
     * 创建一个配置项。
     * @param key 配置项键
     * @param value 配置项值
     * @param description 配置项描述
     * @return 配置项
     */
    private ConfigurationItem defaultItem(String key, JsonNode value, String description) {
        return new ConfigurationItem(key, value, description, 0L, null, null);
    }

    private JsonNode parse(String raw) {
        try {
            JsonNode value = objectMapper.readTree(raw);
            if (value == null || value.isNull()) {
                throw new IllegalStateException("数据库中的系统配置不能为空");
            }
            return value;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("数据库中的系统配置不是有效JSON", exception);
        }
    }

    /** 四项权重均为数字、非负、最多两位小数，且总和大于零。 */
    private AssignmentWeightsSnapshot weightsSnapshot(JsonNode value, Long version) {
        if (!value.isObject() || value.size() != WEIGHT_FIELDS.size()) {
            throw new IllegalArgumentException("分配权重必须包含skill、load、sla、rating四项");
        }
        List<BigDecimal> values = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (String field : WEIGHT_FIELDS) {
            JsonNode node = value.get(field);
            if (node == null || !node.isNumber()) {
                throw new IllegalArgumentException("分配权重必须为数字且不能为空");
            }
            BigDecimal weight = node.decimalValue();
            if (weight.signum() < 0 || weight.compareTo(MAX_WEIGHT) > 0
                    || weight.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException("分配权重须在0至99999999.99之间，最多两位小数");
            }
            values.add(weight);
            total = total.add(weight);
        }
        if (total.signum() <= 0) {
            throw new IllegalArgumentException("分配权重总和必须大于零");
        }
        return new AssignmentWeightsSnapshot(values.get(0), values.get(1), values.get(2), values.get(3), version);
    }

    /**
     * 要求管理员权限。
     * @param operatorRole 操作员角色
     */
    private void requireAdministrator(String operatorRole) {
        if (!"ADMIN".equals(operatorRole)) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }
    }
}

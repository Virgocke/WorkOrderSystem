package com.WorkOrder.model.ticket;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 系统 SLA 分钟数快照，配置保存与工单创建共用校验规则。
 */
@Getter
public final class SlaDefaults {
    /**
     * 尚未保存配置时的响应、解决时限，单位为分钟。
     */
    public static final String DEFAULT_JSON = "{\"responseMin\":30,\"resolutionMin\":240}";
    /**
     * 响应时限，5 至 2880 分钟。
     */
    private final int responseMin;
    /**
     * 解决时限，30 至 10080 分钟。
     */
    private final int resolutionMin;

    /**
     * 构造SLA默认值。
     *
     * @param responseMin 响应时限，5 至 2880 分钟
     * @param resolutionMin 解决时限，30 至 10080 分钟
     */
    private SlaDefaults(int responseMin, int resolutionMin) {
        this.responseMin = responseMin;
        this.resolutionMin = resolutionMin;
    }

    /**
     * 校验完整的系统 SLA 配置。
     *
     * @param value 仅包含 responseMin、resolutionMin 的 JSON 对象
     * @return 校验后的分钟数快照
     */
    public static SlaDefaults fromJson(JsonNode value) {
        if (value == null || !value.isObject() || value.size() != 2) {
            throw new IllegalArgumentException("SLA默认值必须包含responseMin、resolutionMin两项");
        }
        return new SlaDefaults(minutes(value.get("responseMin"), 5, 2880, "响应"),
                minutes(value.get("resolutionMin"), 30, 10080, "解决"));
    }

    /**
     * 处理 defaults 对应的SLA默认值操作。
     *
     * @return 配置记录不存在时使用的默认分钟数
     */
    public static SlaDefaults defaults() {
        return new SlaDefaults(30, 240);
    }

    /**
     * 校验单项整数分钟数，拒绝字符串、小数、空值及越界值。
     *
     * @param value 待校验的整数分钟数节点
     * @param min 允许的分钟数下界，包含该值
     * @param max 允许的分钟数上界，包含该值
     * @param label 错误提示中的时限名称
     * @return 通过校验的 SLA 时限，单位为分钟
     */
    private static int minutes(JsonNode value, int min, int max, String label) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()
                || value.intValue() < min || value.intValue() > max) {
            throw new IllegalArgumentException(label + "时限须为" + min + "至" + max + "分钟的整数");
        }
        return value.intValue();
    }
}

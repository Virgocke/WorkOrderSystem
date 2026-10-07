package com.WorkOrder.assignment.service;

import com.WorkOrder.assignment.dto.SaveConfigurationsDto;
import com.WorkOrder.assignment.model.ConfigurationItem;
import com.WorkOrder.model.assignment.AssignmentWeightsSnapshot;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 系统配置管理与分配权重读取。
 */
public interface ConfigurationService {
    /**
     * 列出当前角色可读取的配置项。
     *
     * @param operatorRole 操作员角色
     * @return 配置项列表
     */
    List<ConfigurationItem> list(String operatorRole);
    /**
     * 保存配置信息。
     *
     * @param dto 配置信息
     * @param operatorId 操作员ID
     * @param operatorRole 操作员角色
     * @return 是否保存成功
     */
    boolean save(SaveConfigurationsDto dto, Long operatorId, String operatorRole);
    /**
     * 获取当前生效的分配权重快照。
     *
     * @return 分配权重快照
     */
    AssignmentWeightsSnapshot getCurrentWeights();
}

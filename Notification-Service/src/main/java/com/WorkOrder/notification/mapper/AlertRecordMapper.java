package com.WorkOrder.notification.mapper;

import com.WorkOrder.notification.model.AlertRecords;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author Virgor
 * @date 2026年09月19日 00:23
 * @description 告警记录Mapper
 */
@Mapper
public interface AlertRecordMapper extends BaseMapper<AlertRecords> {
}

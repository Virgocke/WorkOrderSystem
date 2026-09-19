package com.WorkOrder.sla.mapper;

import com.WorkOrder.sla.model.SlaRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author Virgor
 * @date 2026年09月19日 04:28
 * @description SlaRecordMapper 工单SLA记录Mapper接口
 */
@Mapper
public interface SlaRecordMapper extends BaseMapper<SlaRecord> {
}

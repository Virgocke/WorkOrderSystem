package com.WorkOrder.sla.mapper;

import com.WorkOrder.sla.model.SlaRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年09月19日 04:28
 * @description SlaRecordMapper 工单SLA记录Mapper接口
 */
@Mapper
public interface SlaRecordMapper extends BaseMapper<SlaRecord> {
    /**
     * 从升级事件创建 SLA 快照或只提高已有级别。
     * 保留已有记录的截止时间和响应统计；乱序到达也不能使级别倒退。
     *
     * @return 新增或更新影响的行数；旧级别事件允许返回零
     */
    @Insert("INSERT INTO sla_records "
            + "(ticket_id, response_deadline, resolution_deadline, current_escalation_level) "
            + "VALUES (#{ticketId}, #{responseDeadline}, #{resolutionDeadline}, #{level}) "
            + "ON DUPLICATE KEY UPDATE current_escalation_level = "
            + "GREATEST(COALESCE(current_escalation_level, 0), VALUES(current_escalation_level))")
    int upsertEscalation(@Param("ticketId") long ticketId,
                         @Param("responseDeadline") LocalDateTime responseDeadline,
                         @Param("resolutionDeadline") LocalDateTime resolutionDeadline,
                         @Param("level") int level);

    /** 记录首次解决时间；乱序升级不得清除解决结果，重复解决不得覆盖首次事实。 */
    @Insert("INSERT INTO sla_records "
            + "(ticket_id, response_deadline, resolution_deadline, resolved_at, is_resolution_timeout) "
            + "VALUES (#{ticketId}, #{responseDeadline}, #{resolutionDeadline}, #{resolvedAt}, "
            + "CASE WHEN #{resolvedAt} > #{resolutionDeadline} THEN 1 ELSE 0 END) "
            + "ON DUPLICATE KEY UPDATE "
            + "is_resolution_timeout = CASE WHEN resolved_at IS NULL THEN "
            + "CASE WHEN VALUES(resolved_at) > resolution_deadline THEN 1 ELSE 0 END "
            + "ELSE is_resolution_timeout END, "
            + "resolved_at = COALESCE(resolved_at, VALUES(resolved_at))")
    int upsertResolution(@Param("ticketId") long ticketId,
                         @Param("responseDeadline") LocalDateTime responseDeadline,
                         @Param("resolutionDeadline") LocalDateTime resolutionDeadline,
                         @Param("resolvedAt") LocalDateTime resolvedAt);
}

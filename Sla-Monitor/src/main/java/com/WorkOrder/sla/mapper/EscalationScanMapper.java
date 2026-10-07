package com.WorkOrder.sla.mapper;

import com.WorkOrder.model.ticket.EscalationRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description SLA 扫描只读取工单源表，实际变更由 Ticket-Service 执行。
 */
@Mapper
public interface EscalationScanMapper {
    /**
     * 查询Rules。
     *
     * @return 当前规则 JSON，缺失表示关闭自动升级
     */
    @Select("SELECT config_value FROM configurations WHERE config_key = 'escalationRules'")
    String selectRules();

    /**
     * 使用 ID 游标分页，筛选尚未达到目标级别且至少命中一条规则的活动工单。
     *
     * @param afterId 分页扫描的排除式起始 ID
     * @param now 当前处理时间
     * @param rules 当前生效的升级级别与阈值配置
     * @return 满足升级条件的工单 ID，按分页扫描顺序返回
     */
    @Select({"<script>",
            "SELECT id FROM tickets WHERE id &gt; #{afterId}",
            "AND status IN ('PENDING_ASSIGN','PENDING_RESPONSE','PROCESSING') AND (",
            "<foreach collection='rules' item='rule' separator=' OR '>",
            "(escalated_level &lt; #{rule.level} AND (",
            "(status IN ('PENDING_ASSIGN','PENDING_RESPONSE') AND first_response_at IS NULL",
            "AND response_deadline &lt;= TIMESTAMPADD(MINUTE, -#{rule.responseTimeoutMin}, #{now}))",
            "OR resolution_deadline &lt;= TIMESTAMPADD(MINUTE, -#{rule.resolutionTimeoutMin}, #{now})))",
            "</foreach>",
            ") ORDER BY id LIMIT 200", "</script>"})
    List<Long> selectCandidates(@Param("afterId") long afterId, @Param("now") LocalDateTime now,
                                @Param("rules") List<EscalationRule> rules);
}

package com.WorkOrder.search.mapper;

import com.WorkOrder.search.model.admin.TicketSearchOutboxStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 只读搜索专属 Outbox 聚合，不修改发送状态或重试记录。
 */
@Mapper
public interface TicketSearchDiagnosticsMapper {
    /**
     * 按搜索 Topic、事件类型及生产服务采集新记录、重试、发送中和终态失败。
     *
     * @param topic 消息主题
     * @param staleSeconds stale秒数
     * @return 工单搜索Outbox状态
     */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    TicketSearchOutboxStatus selectOutboxStatus(@Param("topic") String topic,
                                              @Param("staleSeconds") int staleSeconds);
}

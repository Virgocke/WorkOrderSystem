package com.WorkOrder.search.mapper;

import com.WorkOrder.search.model.TicketSearchWriteTargetRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 一次查询共享控制记录及当前任务，不从本地进程缓存选择写目标。
 */
@Mapper
public interface TicketSearchWriteTargetMapper {
    /**
     * 关闭二级缓存并清理当前会话缓存，目标切换后的下一次请求重新查询。
     *
     * @return 工单搜索写入目标记录
     */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    TicketSearchWriteTargetRecord selectCurrent();
}

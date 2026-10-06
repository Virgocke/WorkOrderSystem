package com.WorkOrder.search.mapper;

import com.WorkOrder.search.model.TicketIndexSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 从主业务数据源一次读取已提交的完整工单字段与源版本。 */
@Mapper
public interface TicketIndexSourceMapper {
    /** 每次回源重新读取，避免重复消息复用 MyBatis 缓存中的旧工单。 */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    TicketIndexSource selectById(@Param("ticketId") Long ticketId);

    /** 返回本轮扫描的有限主键上界；空表返回 0，不将它当作事务提交水位。 */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Long selectMaxId();

    /**
     * 主键游标短读完整投影和版本，允许同一轮在不同时间读取各批已提交行。
     *
     * @param afterId 已确认整批的最后主键，不包含此行
     * @param upperId 本轮固定上界，包含此行
     * @param limit 服务端配置的批次上限
     * @return 按主键升序排列的完整源行
     */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<TicketIndexSource> selectBatch(@Param("afterId") long afterId,
                                      @Param("upperId") long upperId,
                                      @Param("limit") int limit);
}

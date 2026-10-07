package com.WorkOrder.ticket.mapper;

import com.WorkOrder.ticket.model.TicketNumberCounter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 工单编号规则与计数器的持久化操作。
 */
@Mapper
public interface TicketNumberMapper {
    /**
     * 查询规则JSON。
     *
     * @return 数据库中的规则 JSON；尚未保存时为空
     */
    @Select("SELECT config_value FROM configurations WHERE config_key = 'ticketNoRule'")
    String selectRuleJson();

    /**
     * 首次使用计数范围时创建计数行；重复使用返回 0。
     *
     * @param scopeKey 前缀与日期组成的计数范围键
     * @return 新建行数，0 或 1
     */
    @Insert("INSERT IGNORE INTO ticket_number_counters (scope_key, `last_value`) VALUES (#{scopeKey}, 0)")
    int insertCounterIfAbsent(@Param("scopeKey") String scopeKey);

    /**
     * 读取历史同范围编号的最大尾号，只用于新计数范围初始化。
     *
     * @param scopeKey 前缀与日期组成的计数范围键
     * @return 已有编号中 1 至 6 位纯数字尾号的最大值，没有时为 0
     */
    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(ticket_no, CHAR_LENGTH(#{scopeKey}) + 1) AS UNSIGNED)), 0) "
            + "FROM tickets WHERE ticket_no LIKE CONCAT(#{scopeKey}, '%') "
            + "AND ticket_no REGEXP CONCAT('^', #{scopeKey}, '[0-9]{1,6}$')")
    Long selectMaxExistingSequence(@Param("scopeKey") String scopeKey);

    /**
     * 对首次创建的计数行写入已有编号的最大尾号。
     *
     * @param scopeKey 前缀与日期组成的计数范围键
     * @param lastValue 已有编号的最大尾号
     * @return 修改行数
     */
    @Update("UPDATE ticket_number_counters SET `last_value` = #{lastValue} WHERE scope_key = #{scopeKey}")
    int seedCounter(@Param("scopeKey") String scopeKey, @Param("lastValue") long lastValue);

    /**
     * 锁定计数行，使并发建单依次获取序号。
     *
     * @param scopeKey 前缀与日期组成的计数范围键
     * @return 当前最大序号
     */
    @Select("SELECT scope_key, `last_value`, created_at, updated_at "
            + "FROM ticket_number_counters WHERE scope_key = #{scopeKey} FOR UPDATE")
    @Results({
            @Result(property = "scopeKey", column = "scope_key", id = true),
            @Result(property = "lastValue", column = "last_value"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    TicketNumberCounter lockCounter(@Param("scopeKey") String scopeKey);

    /**
     * 在持有行锁的建单事务内增加序号。
     *
     * @param scopeKey 前缀与日期组成的计数范围键
     * @param expected 当前最大序号
     * @return 修改行数，成功为 1
     */
    @Update("UPDATE ticket_number_counters SET `last_value` = `last_value` + 1 "
            + "WHERE scope_key = #{scopeKey} AND `last_value` = #{expected}")
    int incrementCounter(@Param("scopeKey") String scopeKey, @Param("expected") long expected);
}

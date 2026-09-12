package com.WorkOrder.ticket.mapper;

import com.WorkOrder.ticket.model.TicketCategory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author Virgor
 * @date 2026年09月13日 03:08
 * @description 工单分类Mapper
 */
@Mapper
public interface TicketCategoryMapper extends BaseMapper<TicketCategory> {
}

package com.WorkOrder.file.mapper;

import com.WorkOrder.ticket.model.Attachment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;


/**
 * @author Virgor
 * @date 2026年09月24日 02:06
 * @description 文件附件Mapper
 */
@Mapper
public interface AttachmentMapper extends BaseMapper<Attachment> {
}

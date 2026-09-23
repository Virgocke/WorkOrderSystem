package com.WorkOrder.file.service;

import com.WorkOrder.ticket.dto.FileDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Virgor
 * @date 2026年09月24日 02:40
 * @description 文件上传服务
 */
public interface AttachmentService {

    /**
     * 上传图片
     * @param uploaderId 上传者ID
     * @param file 文件
     * @return 文件信息
     */
    FileDto uploadImage(Long uploaderId, MultipartFile file);

    /**
     * 将当前用户上传的临时附件绑定到新工单。
     */
    void bindToTicket(Long uploaderId, Long ticketId, List<Long> attachmentIds);

    /**
     * 批量获取工单附件的临时预览地址。
     */
    Map<Long, List<String>> getAttachmentUrlsByTicketIds(Collection<Long> ticketIds);
}

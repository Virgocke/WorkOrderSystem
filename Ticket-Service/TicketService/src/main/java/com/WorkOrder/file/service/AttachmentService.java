package com.WorkOrder.file.service;

import com.WorkOrder.file.dto.FileDto;
import com.WorkOrder.file.dto.FilePreviewDto;
import com.WorkOrder.ticket.model.Attachment;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Virgor
 * @date 2026年09月24日 02:40
 * @description 文件上传服务
 */
public interface AttachmentService extends IService<Attachment> {

    /**
     * 上传图片
     *
     * @param uploaderId 上传者ID
     * @param file 文件
     * @return 文件信息
     */
    FileDto uploadImage(Long uploaderId, MultipartFile file);

    /**
     * 将当前用户上传的临时附件绑定到新工单。
     *
     * @param uploaderId uploader ID
     * @param ticketId 工单 ID
     * @param attachmentIds 附件 ID 集合
     */
    void bindToTicket(Long uploaderId, Long ticketId, List<Long> attachmentIds);

    /**
     * 批量获取工单附件的短期预览地址。
     *
     * @param ticketIds 工单 ID 集合
     * @return 键值映射
     */
    Map<Long, List<String>> getAttachmentUrlsByTicketIds(Collection<Long> ticketIds);

    /**
     * 批量获取操作日志附件的短期预览地址。
     *
     * @param operationLogIds operation日志 ID 集合
     * @return 键值映射
     */
    Map<Long, List<String>> getAttachmentUrlsByOperationLogIds(Collection<Long> operationLogIds);

    /**
     * 将当前用户上传的临时附件绑定到操作日志。
     *
     * @param uploaderId uploader ID
     * @param ticketId 工单 ID
     * @param operationLogId operation日志 ID
     * @param attachmentIds 附件 ID 集合
     */
    void bindToOperationLog(
            Long uploaderId,
            Long ticketId,
            Long operationLogId,
            List<Long> attachmentIds
    );

    /**
     * 获取文件预览信息
     *
     * @param attachmentId 附件 ID
     * @param userId 用户 ID
     * @param currentUserRole 当前用户角色
     * @return 文件Preview请求数据
     */
    FilePreviewDto getPreview(Long attachmentId, Long userId, String currentUserRole);
}

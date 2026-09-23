package com.WorkOrder.file.service.impl;

import com.WorkOrder.file.mapper.AttachmentMapper;
import com.WorkOrder.file.service.AttachmentService;
import com.WorkOrder.file.service.MinioService;
import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.ticket.dto.FileDto;
import com.WorkOrder.ticket.model.Attachment;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.minio.ObjectWriteResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @author Virgor
 * @date 2026年09月24日 02:42
 * @description
 */
@Service
@Slf4j
public class AttachmentServiceImpl implements AttachmentService {

    private static final long MAX_SIZE = 20L * 1024 * 1024; // 20MB

    private static final DateTimeFormatter OBJECT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd"); // 日期格式：年/月/日

    private final AttachmentMapper attachmentMapper;
    private final MinioService minioService;
    private final String bucketName;

    // 构造器，用于依赖注入
    public AttachmentServiceImpl(AttachmentMapper attachmentMapper,
                                 MinioService minioService,
                                 @Value("${minio.bucket-name}") String bucketName) {
        this.attachmentMapper = attachmentMapper;
        this.minioService = minioService;
        this.bucketName = bucketName;
    }

    /**
     * 上传图片附件
     * @param uploaderId 上传者ID
     * @param file 文件
     * @return 附件信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileDto uploadImage(Long uploaderId, MultipartFile file) {
        if (uploaderId == null) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_OFFLINE);
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new SystemException(SystemExceptionEnum.ATTACHMENT_SIZE_EXCEEDED);
        }

        // 检测文件类型
        ImageType imageType = detectImageType(file);
        if (imageType == null) {
            throw new SystemException(SystemExceptionEnum.UNSUPPORTED_ATTACHMENT_TYPE);
        }

        // 获得原始文件名
        String originalName = normalizeOriginalName(file.getOriginalFilename(), imageType.extension);
        // 构建对象名
        String objectName = buildObjectName(uploaderId, imageType.extension);
        boolean minioOperationStarted = false;

        try (InputStream inputStream = file.getInputStream()) {
            minioOperationStarted = true;
            ObjectWriteResponse response = minioService.uploadFile(
                    objectName,
                    inputStream,
                    file.getSize(),
                    imageType.contentType
            );

            LocalDateTime now = LocalDateTime.now();
            Attachment attachment = new Attachment();
            attachment.setUploaderId(uploaderId);
            attachment.setBucket(bucketName);
            attachment.setObjectKey(objectName);
            attachment.setOriginalName(originalName);
            attachment.setContentType(imageType.contentType);
            attachment.setSize(file.getSize());
            attachment.setStatus("TEMP");
            attachment.setExpiresAt(now.plusHours(24));
            attachment.setCreatedAt(now);

            if (attachmentMapper.insert(attachment) != 1) {
                throw new IllegalStateException("保存附件元数据失败");
            }

            FileDto fileDto = new FileDto();

            String url = minioService.getPresignedUrl(objectName, 7 * 24 * 60 * 60);

            fileDto.setUrl(url);
            fileDto.setId(attachment.getId());
            fileDto.setName(originalName);
            fileDto.setSize(file.getSize());
            fileDto.setType(imageType.contentType);
            return fileDto;
        } catch (Exception exception) {
            if (minioOperationStarted) {
                compensateMinioObject(objectName, exception);
            }
            if (exception instanceof RuntimeException) {
                throw (RuntimeException) exception;
            }
            throw new IllegalStateException("上传附件失败", exception);
        }
    }

    /**
     * 绑定附件到工单
     * @param uploaderId 上传者ID
     * @param ticketId 工单ID
     * @param attachmentIds 附件ID列表
    */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindToTicket(Long uploaderId, Long ticketId, List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        if (uploaderId == null || ticketId == null) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }

        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long attachmentId : attachmentIds) {
            if (attachmentId == null || attachmentId <= 0 || !uniqueIds.add(attachmentId)) {
                throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
            }
        }
        if (uniqueIds.size() > 9) {
            throw new IllegalArgumentException("工单最多上传 9 个附件");
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<Attachment> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.in(Attachment::getId, uniqueIds)
                .eq(Attachment::getUploaderId, uploaderId)
                .eq(Attachment::getStatus, "TEMP")
                .isNull(Attachment::getTicketId)
                .isNull(Attachment::getOperationLogId)
                .and(wrapper -> wrapper.isNull(Attachment::getExpiresAt)
                        .or()
                        .gt(Attachment::getExpiresAt, now))
                .set(Attachment::getTicketId, ticketId)
                .set(Attachment::getStatus, "BOUND")
                .set(Attachment::getBoundAt, now)
                .set(Attachment::getExpiresAt, null);

        int updated = attachmentMapper.update(null, updateWrapper);
        if (updated != uniqueIds.size()) {
            throw new IllegalArgumentException("附件不存在、已过期、已绑定或不属于当前用户");
        }
    }

    /**
     * 根据工单ID获取附件URL列表
     * @param ticketIds 工单ID集合
     * @return 工单ID与附件URL列表的映射
     */
    @Override
    public Map<Long, List<String>> getAttachmentUrlsByTicketIds(Collection<Long> ticketIds) {
        if (ticketIds == null || ticketIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 过滤掉空值并去重
        Set<Long> uniqueTicketIds = ticketIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (uniqueTicketIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 查询附件列表
        List<Attachment> attachments = attachmentMapper.selectList(
                new LambdaQueryWrapper<Attachment>()
                        .in(Attachment::getTicketId, uniqueTicketIds)
                        .eq(Attachment::getStatus, "BOUND")
                        .isNull(Attachment::getOperationLogId)
                        .orderByAsc(Attachment::getId)
        );

        // 按工单ID分组，生成预览地址
        Map<Long, List<String>> urlsByTicketId = new LinkedHashMap<>();
        for (Attachment attachment : attachments) {
            try {
                // 生成预览地址，有效期 1 小时
                String url = minioService.getPresignedUrl(attachment.getObjectKey(), 60 * 60);
                urlsByTicketId
                        .computeIfAbsent(attachment.getTicketId(), ignored -> new ArrayList<>())
                        .add(url);
            } catch (Exception exception) {
                throw new IllegalStateException("生成附件预览地址失败", exception);
            }
        }
        return urlsByTicketId;
    }

    /**
     * 构建对象名
     * @param uploaderId 上传者ID
     * @param extension 文件扩展名
     * @return 对象名
     */
    private String buildObjectName(Long uploaderId, String extension) {
        return "ticket/" + uploaderId + "/picture/"
                + LocalDate.now().format(OBJECT_DATE_FORMATTER) + "/"
                + UUID.randomUUID() + extension;
    }

    /**
     * 规范化原始文件名
     * @param originalName 原始文件名
     * @param defaultExtension 默认扩展名
     * @return 规范化后的文件名
     */
    private String normalizeOriginalName(String originalName, String defaultExtension) {
        if (originalName == null || originalName.trim().isEmpty()) {
            return "image" + defaultExtension;
        }

        String normalized = originalName.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        if (normalized.isEmpty()) {
            normalized = "image" + defaultExtension;
        }
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }

    /**
     * 补偿 MinIO 对象
     * @param objectName 对象名
     * @param originalException 原始异常
     */
    private void compensateMinioObject(String objectName, Exception originalException) {
        try {
            minioService.remove(objectName);
        } catch (Exception cleanupException) {
            originalException.addSuppressed(cleanupException);
            log.warn("附件上传失败后清理 MinIO 对象失败，objectName={}", objectName, cleanupException);
        }
    }

    /**
     * 检测文件类型
     * @param file 文件
     * @return 文件类型
     */
    private ImageType detectImageType(MultipartFile file) {
        byte[] header = new byte[12];
        int length;
        try (InputStream inputStream = file.getInputStream()) {
            length = readHeader(inputStream, header);
        } catch (IOException exception) {
            throw new IllegalStateException("读取上传文件失败", exception);
        }

        if (length >= 3 && matches(header, 0, 0xFF, 0xD8, 0xFF)) {
            return ImageType.JPEG;
        }
        if (length >= 8 && matches(header, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return ImageType.PNG;
        }
        if (length >= 6 && (asciiEquals(header, 0, "GIF87a") || asciiEquals(header, 0, "GIF89a"))) {
            return ImageType.GIF;
        }
        if (length >= 2 && asciiEquals(header, 0, "BM")) {
            return ImageType.BMP;
        }
        if (length >= 12 && asciiEquals(header, 0, "RIFF") && asciiEquals(header, 8, "WEBP")) {
            return ImageType.WEBP;
        }
        return null;
    }

    /**
     * 读取文件头
     * @param inputStream 输入流
     * @param header 文件头
     * @return 读取的字节数
     * @throws IOException 如果读取输入流时发生 I/O 错误
     */
    private int readHeader(InputStream inputStream, byte[] header) throws IOException {
        int offset = 0;
        while (offset < header.length) {
            int count = inputStream.read(header, offset, header.length - offset);
            if (count < 0) {
                break;
            }
            offset += count;
        }
        return offset;
    }

    /**
     * 检查字节数组是否匹配给定的值
     * @param bytes 要检查的字节数组
     * @param offset 偏移量
     * @param expected 要匹配的值
     * @return 如果字节数组匹配给定的值，则返回 true；否则返回 false
     */
    private boolean matches(byte[] bytes, int offset, int... expected) {
        if (offset + expected.length > bytes.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if ((bytes[offset + index] & 0xFF) != expected[index]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查字节数组是否与给定的 ASCII 字符串匹配
     * @param bytes 要检查的字节数组
     * @param offset 偏移量
     * @param expected 要匹配的 ASCII 字符串
     * @return 如果字节数组与给定的 ASCII 字符串匹配，则返回 true；否则返回 false
     */
    private boolean asciiEquals(byte[] bytes, int offset, String expected) {
        byte[] expectedBytes = expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (offset + expectedBytes.length > bytes.length) {
            return false;
        }
        for (int index = 0; index < expectedBytes.length; index++) {
            if (bytes[offset + index] != expectedBytes[index]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 枚举类，表示图片类型
     */
    private enum ImageType {
        JPEG(".jpg", "image/jpeg"),
        PNG(".png", "image/png"),
        GIF(".gif", "image/gif"),
        WEBP(".webp", "image/webp"),
        BMP(".bmp", "image/bmp");

        private final String extension;
        private final String contentType;

        ImageType(String extension, String contentType) {
            this.extension = extension.toLowerCase(Locale.ROOT);
            this.contentType = contentType;
        }
    }
}

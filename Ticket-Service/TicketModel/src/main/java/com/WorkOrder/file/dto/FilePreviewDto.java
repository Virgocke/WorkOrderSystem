package com.WorkOrder.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 文件预览接口的响应数据。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FilePreviewDto {

    private Long id;

    /**
     * MinIO 临时预签名地址
     */
    private String previewUrl;

    /**
     * 地址过期时间
     */
    private LocalDateTime expiresAt;

    private String name;

    private String contentType;

    private Long size;
}

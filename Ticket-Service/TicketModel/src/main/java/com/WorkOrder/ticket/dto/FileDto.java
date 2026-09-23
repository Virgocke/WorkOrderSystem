package com.WorkOrder.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Virgor
 * @date 2026年09月24日 01:53
 * @description 文件信息数据传输对象
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileDto {
    private Long id;
    private String url;
    private String name;
    private long size;
    private String type;
}

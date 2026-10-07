package com.WorkOrder.handler.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年09月16日 02:37
 * @description 部门的数据库记录。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("departments")
public class Department {
    private Long id;
    private String name;
    private Long parentId;
    private Long managerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.WorkOrder.ticket.controller;

import com.WorkOrder.file.dto.FilePreviewDto;
import com.WorkOrder.file.service.AttachmentService;
import com.WorkOrder.model.Result;
import com.WorkOrder.security.CurrentUserIdProvider;
import com.WorkOrder.file.dto.FileDto;
import com.WorkOrder.security.CurrentUserRoleProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author Virgor
 * @date 2026年09月24日 01:50
 * @description 文件上传功能
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/files")
public class FileController {

    private final AttachmentService attachmentService;
    private final CurrentUserIdProvider currentUserIdProvider;
    private final CurrentUserRoleProvider currentUserRoleProvider;


    /**
     * 上传文件
     * @param file 文件
     * @param authentication 认证信息
     * @return 文件信息
     */
    @PostMapping
    public Result<FileDto> upload(@RequestParam("file") MultipartFile file,
                                  Authentication authentication) {
        Long userId = currentUserIdProvider.get(authentication);
        return Result.success(attachmentService.uploadImage(userId, file));
    }


    /**
     * 文件预览
     * @param id 文件ID
     * @param authentication 认证信息
     * @return 文件预览信息
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/preview")
    public Result<FilePreviewDto> preview(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserIdProvider.get(authentication);
        String userRole = currentUserRoleProvider.get(authentication);
        return Result.success(
                attachmentService.getPreview(id, userId, userRole)
        );
    }
}


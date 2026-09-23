package com.WorkOrder.ticket.controller;

import com.WorkOrder.file.service.AttachmentService;
import com.WorkOrder.model.Result;
import com.WorkOrder.security.CurrentUserIdProvider;
import com.WorkOrder.ticket.dto.FileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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


    @PostMapping
    public Result<FileDto> upload(@RequestParam("file") MultipartFile file,
                                  Authentication authentication) {
        Long userId = currentUserIdProvider.get(authentication);
        return Result.success(attachmentService.uploadImage(userId, file));
    }
}

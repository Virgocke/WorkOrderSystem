package com.WorkOrder.controller;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.handler.HandlerProfile;
import com.WorkOrder.handler.service.HandlerUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月16日 02:29
 * @description 处理人控制器
 */
@RestController
@RequestMapping("/handlers")
@RequiredArgsConstructor
public class HandlerController {

    private final HandlerUserService handlerService;
    @GetMapping("/options")
    public Result<List<HandlerProfile>> getHandler() {
        return Result.success(handlerService.getHandler());
    }
}

package com.WorkOrder.controller;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.handler.SkillTag;
import com.WorkOrder.skill.service.SkillTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 技能标签目录接口。 */
@RestController
@RequestMapping("/skills")
@RequiredArgsConstructor
public class SkillTagController {

    private final SkillTagService skillTagService;

    /**
     * 查询技能标签及每个标签的处理人使用数。
     *
     * @return 技能标签列表
     */
    @GetMapping
    public Result<List<SkillTag>> listSkillTags() {
        return Result.success(skillTagService.listSkillTags());
    }
}

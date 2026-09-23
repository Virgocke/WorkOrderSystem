package com.WorkOrder.controller;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.handler.SkillTag;
import com.WorkOrder.skill.dto.CreateSkillTagRequest;
import com.WorkOrder.skill.dto.UpdateSkillTagRequest;
import com.WorkOrder.skill.service.SkillTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
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

    /**
     * 新增技能标签。
     *
     * @param request 技能创建参数
     * @return 新建后的技能标签
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result<SkillTag> createSkillTag(
            @Valid @RequestBody CreateSkillTagRequest request) {
        return Result.success(skillTagService.createSkillTag(request));
    }

    /**
     * 部分更新技能标签。
     *
     * @param id 技能 ID
     * @param request 待更新字段
     * @return 更新后的技能标签
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Result<SkillTag> updateSkillTag(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSkillTagRequest request) {
        return Result.success(skillTagService.updateSkillTag(id, request));
    }

    /**
     * 删除未被处理人使用的技能标签。
     *
     * @param id 技能 ID
     * @return 删除成功时返回 {@code true}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteSkillTag(@PathVariable Long id) {
        return Result.success(skillTagService.deleteSkillTag(id));
    }
}

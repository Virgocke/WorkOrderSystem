package com.WorkOrder.ticket.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.AssertTrue;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月15日 23:50
 * @description 回复 / 补充描述 DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketReplyDto {
    private String content;
    private List<Long> attachmentIds;

    /**
     * 检查内容或附件是否至少有一个
     *
     * @return true 如果内容或附件至少有一个非空且非空字符串
     */
    @JsonIgnore
    @AssertTrue
    public boolean isContentOrAttachmentsPresent(){
        // 检查内容或附件是否至少有一个
        boolean hasContent = content != null && !content.trim().isEmpty();

        // 检查附件列表是否至少有一个非空且非空字符串
        boolean hasAttachment =
                attachmentIds != null
                && attachmentIds.stream()
                    .anyMatch(item -> item != null && item != 0);

        return hasContent || hasAttachment;
    }
}

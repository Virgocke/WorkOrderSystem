package com.WorkOrder.ticket.service;

import com.WorkOrder.file.service.AttachmentService;
import com.WorkOrder.model.ticket.TicketResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 为工单响应统一补充附件短期预览 URL。
 */
@Component
@RequiredArgsConstructor
public class TicketResponseAttachmentEnricher {

    private final AttachmentService attachmentService;

    /**
     * 为单个工单响应补充附件 URL。
     *
     * @param response 工单详情
     * @return 工单详情
     */
    public TicketResponse enrich(TicketResponse response) {
        Objects.requireNonNull(response, "工单响应不能为空");
        if (response.getId() == null) {
            response.setAttachmentUrls(Collections.emptyList());
            return response;
        }
        return enrichAll(Collections.singletonList(response)).get(0);
    }

    /**
     * 为一组工单响应批量补充附件 URL，避免列表接口逐条查询附件。
     *
     * @param responses 需要补充附件预览地址的工单响应集合
     * @return 工单详情列表
     */
    public List<TicketResponse> enrichAll(List<TicketResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> ticketIds = responses.stream()
                .filter(Objects::nonNull)
                .map(TicketResponse::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, List<String>> urlsByTicketId =
                attachmentService.getAttachmentUrlsByTicketIds(ticketIds);

        for (TicketResponse response : responses) {
            if (response != null) {
                response.setAttachmentUrls(urlsByTicketId.getOrDefault(
                        response.getId(), Collections.emptyList()));
            }
        }
        return responses;
    }
}

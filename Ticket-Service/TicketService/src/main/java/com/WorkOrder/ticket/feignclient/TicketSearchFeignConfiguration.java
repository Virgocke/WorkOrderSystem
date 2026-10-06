package com.WorkOrder.ticket.feignclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/** 仅由工单搜索客户端加载，保留共用 Bearer 透传并增加搜索未就绪解码。 */
public class TicketSearchFeignConfiguration extends TicketFeignConfiguration {

    /** @return 不影响用户、派单等其他 Feign 客户端的搜索专属错误解码器 */
    @Bean
    public ErrorDecoder ticketSearchErrorDecoder(ObjectMapper objectMapper) {
        return new TicketSearchErrorDecoder(objectMapper);
    }
}

package com.WorkOrder.ticket.feignclient;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 搜索专属错误解码；只传播明确的未就绪业务码，其他 HTTP 错误沿用 Feign 规则。
 */
public class TicketSearchErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;
    private final ErrorDecoder fallback = new ErrorDecoder.Default();

    /**
     * 复用应用 JSON 解析器，不向外部响应透传远端错误正文。
     *
     * @param objectMapper JSON 序列化与反序列化组件
     */
    public TicketSearchErrorDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 识别 503 响应中的精确业务码，回放已读取的正文供默认解码器处理其他失败。
     *
     * @param methodKey 被调用的 Feign 方法
     * @param response Search-Service HTTP 响应
     * @return 已知的未就绪异常或默认 Feign 异常
     */
    @Override
    public Exception decode(String methodKey, Response response) {
        // 只有 503 可能是约定的搜索未就绪响应；鉴权、参数等其他错误保留 Feign 默认语义。
        if (response.status() != 503 || response.body() == null) {
            return fallback.decode(methodKey, response);
        }
        byte[] body;
        try (InputStream input = response.body().asInputStream()) {
            body = StreamUtils.copyToByteArray(input);
        } catch (IOException exception) {
            return fallback.decode(methodKey, response);
        }
        // 响应流只能读一次，缓存后重新组装响应，让未识别的错误仍可交给默认解码器。
        Response buffered = response.toBuilder().body(body).build();
        try {
            JsonNode code = objectMapper.readTree(body).path("code");
            // 同时匹配 HTTP 503 和精确业务码，避免把远端任意 503 都误译成索引未就绪。
            if (code.isIntegralNumber() && code.canConvertToInt()
                    && code.intValue() == SystemExceptionEnum.TICKET_SEARCH_NOT_READY.getCode()) {
                return new SystemException(SystemExceptionEnum.TICKET_SEARCH_NOT_READY);
            }
        } catch (IOException | RuntimeException ignored) {
            // 无法识别的错误仍保留原 HTTP 状态，不伪装成搜索未就绪。
        }
        return fallback.decode(methodKey, buffered);
    }
}

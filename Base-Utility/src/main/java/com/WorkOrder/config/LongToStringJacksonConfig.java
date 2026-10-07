package com.WorkOrder.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 统一将响应中的包装类型 Long 序列化为字符串，避免前端 JavaScript 数值精度丢失。
 */
@Configuration
public class LongToStringJacksonConfig {

    /**
     * 注册 Long 与 long 的字符串序列化器，避免前端丢失整数精度。
     *
     * @return 包含长整型序列化器的 Jackson 模块
     */
    @Bean
    public Module longToStringModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        return module;
    }
}

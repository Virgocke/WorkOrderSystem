package com.WorkOrder.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Virgor
 * @date 2026年09月11日 19:32
 * @description 自定义Date序列化
 */
@Configuration
public class LocalDateTimeConfig {

    /**
     * 自定义Date序列化
     *
     * @return 自定义的Date序列化器
     */
    @Bean
    public JsonSerializer<Date> dateJsonSerializable(){
        return new JsonSerializer<Date>() {
            /**
             * 按统一日期时间格式写入 JSON。
             *
             * @param date 待序列化的日期时间
             * @param jsonGenerator JSON 输出生成器
             * @param serializerProvider Jackson 序列化上下文
             * @throws IOException 处理过程中发生IO异常时
             */
            @Override
            public void serialize(Date date, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String formattedDate = format.format(date);
                jsonGenerator.writeString(formattedDate);
            }
        };
    }
}

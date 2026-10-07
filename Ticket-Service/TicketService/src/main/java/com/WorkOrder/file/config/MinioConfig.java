package com.WorkOrder.file.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 创建使用外部化凭证的 MinIO 客户端。
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    /**
     * 根据对象存储配置创建 MinIO 客户端。
     *
     * @param properties MinIO 连接与认证配置
     * @return 配置完成的 MinIO 客户端
     */
    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}

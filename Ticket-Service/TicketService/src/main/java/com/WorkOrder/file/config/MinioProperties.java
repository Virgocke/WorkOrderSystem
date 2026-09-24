package com.WorkOrder.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

/** MinIO 连接配置，访问密钥必须由外部环境注入。 */
@Data
@Validated
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    @NotBlank
    private String endpoint;

    @NotBlank
    private String accessKey;

    @NotBlank
    private String secretKey;

    @NotBlank
    private String bucketName;
}

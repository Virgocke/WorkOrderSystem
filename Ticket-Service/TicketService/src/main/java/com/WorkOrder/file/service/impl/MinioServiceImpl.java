package com.WorkOrder.file.service.impl;

import com.WorkOrder.file.service.MinioService;
import io.minio.*;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * @author Virgor
 * @date 2026年09月24日 02:00
 * @description Minio 服务实现
 */
@Service
public class MinioServiceImpl implements MinioService {

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String defaultBucket;

    private static final long MAX_SIZE = 20L * 1024 * 1024;// 20MB

    /**
     * 上传文件
     *
     * @param objectName 对象名
     * @param stream 文件流
     * @param size 文件大小
     * @param contentType 文件内容类型
     * @return 对象写入响应
     * @throws Exception 处理过程中发生异常时
     */
    @Override
    public ObjectWriteResponse upload(String objectName, InputStream stream, long size, String contentType) throws Exception{
        return minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(defaultBucket)
                        .object(objectName)
                        .stream(stream, size, -1)
                        .contentType(contentType)
                        .build()
        );
    }

    /**
     * 下载文件
     *
     * @param objectName 对象名
     * @return 文件流
     * @throws Exception 处理过程中发生异常时
     */
    @Override
    public InputStream download(String objectName) throws Exception{
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(defaultBucket)
                        .object(objectName)
                        .build()
        );
    }

    /**
     * 删除文件
     *
     * @param objectName 对象名
     * @throws Exception 处理过程中发生异常时
     */
    @Override
    public void remove(String objectName) throws Exception{
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(defaultBucket)
                        .object(objectName)
                        .build()
        );
    }

    /**
     * 上传文件，用于文件上传控制类API
     *
     * @param objectName 对象名
     * @param inputStream 文件流
     * @param size 文件大小
     * @param contentType 文件内容类型
     * @return 对象写入响应
     * @throws Exception 读取上传数据或请求 MinIO 失败时
     */
    @Override
    public ObjectWriteResponse uploadFile(String objectName, InputStream inputStream, long size, String contentType) throws Exception{
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException("只允许上传图片文件");
        }
        if (size > MAX_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过20MB");
        }
        ObjectWriteResponse objectWriteResponse = upload(objectName, inputStream, size, contentType);
        if (objectWriteResponse == null) {
            throw new Exception("上传文件失败");
        }

        // 返回文件信息
        return objectWriteResponse;
    }

    /**
     * 获取预签名 URL
     *
     * @param objectName 对象名
     * @param expireSeconds 过期时间（秒）
     * @return 预签名 URL
     * @throws Exception 处理过程中发生异常时
     */
    @Override
    public String getPresignedUrl(String objectName, int expireSeconds) throws Exception {
    return minioClient.getPresignedObjectUrl(
        GetPresignedObjectUrlArgs.builder()
            .method(Method.GET)
            .bucket(defaultBucket)
            .object(objectName)
            .expiry(expireSeconds, TimeUnit.SECONDS)
            .build()
    );
}
}

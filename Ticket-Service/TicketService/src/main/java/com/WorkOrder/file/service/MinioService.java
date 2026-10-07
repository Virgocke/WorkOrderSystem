package com.WorkOrder.file.service;

import io.minio.ObjectWriteResponse;

import java.io.InputStream;

/**
 * @author Virgor
 * @date 2026年09月24日 01:59
 * @description 文件服务接口，提供文件上传、下载、删除功能
 */
public interface MinioService {
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
    ObjectWriteResponse upload(String objectName, InputStream stream, long size, String contentType) throws Exception;

    /**
     * 下载文件
     *
     * @param objectName 对象名
     * @return 文件流
     * @throws Exception 处理过程中发生异常时
     */
    InputStream download(String objectName) throws Exception;

    /**
     * 删除文件
     *
     * @param objectName 对象名
     * @throws Exception 处理过程中发生异常时
     */
    void remove(String objectName) throws Exception;

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
    ObjectWriteResponse uploadFile(String objectName, InputStream inputStream, long size, String contentType) throws Exception;

    /**
     * 生成对象的临时只读访问地址。
     *
     * @param objectName 对象名
     * @param expireSeconds 有效期（秒）
     * @return 预签名 URL
     * @throws Exception 处理过程中发生异常时
     */
    String getPresignedUrl(String objectName, int expireSeconds) throws Exception;
}

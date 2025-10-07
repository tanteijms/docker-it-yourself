package com.diy.service;

import com.diy.entity.UploadSession;

import java.io.IOException;
import java.io.InputStream;

/**
 * 上传业务服务接口
 * 处理Docker镜像分片上传功能
 * 
 * @author diy
 */
public interface UploadService {

    /**
     * 开始新的上传会话
     * 
     * @param repository 仓库名
     * @return 上传会话实体
     */
    UploadSession startUploadSession(String repository);

    /**
     * 根据UUID获取上传会话
     * 
     * @param uuid 会话UUID
     * @return 上传会话实体
     * @throws com.diy.exception.UploadSessionNotFoundException 当会话不存在时
     */
    UploadSession getUploadSession(String uuid);

    /**
     * 上传数据块（分片上传）
     * 
     * @param uuid          会话UUID
     * @param inputStream   数据流
     * @param expectedRange 期望的范围（格式：start-end）
     * @return 更新后的上传会话
     * @throws IOException                                      IO异常
     * @throws com.diy.exception.UploadSessionNotFoundException 当会话不存在时
     * @throws IllegalArgumentException                         当范围不匹配时
     */
    UploadSession uploadChunk(String uuid, InputStream inputStream, String expectedRange) throws IOException;

    /**
     * 完成上传并验证
     * 
     * @param uuid           会话UUID
     * @param expectedDigest 期望的SHA256值
     * @return 创建的Blob实体
     * @throws IOException                                      IO异常
     * @throws com.diy.exception.UploadSessionNotFoundException 当会话不存在时
     * @throws com.diy.exception.InvalidDigestException         当digest不匹配时
     */
    com.diy.entity.Blob completeUpload(String uuid, String expectedDigest) throws IOException;

    /**
     * 取消上传会话
     * 
     * @param uuid 会话UUID
     * @return 是否取消成功
     */
    boolean cancelUploadSession(String uuid);

    /**
     * 检查上传会话是否存在且有效
     * 
     * @param uuid 会话UUID
     * @return 是否存在且有效
     */
    boolean isUploadSessionValid(String uuid);

    /**
     * 获取上传会话的当前状态
     * 
     * @param uuid 会话UUID
     * @return 上传状态信息
     */
    UploadStatus getUploadStatus(String uuid);

    /**
     * 清理过期的上传会话
     * 
     * @return 清理的会话数量
     */
    int cleanupExpiredSessions();

    /**
     * 获取活跃上传会话统计
     * 
     * @return 统计信息
     */
    UploadStats getUploadStats();

    /**
     * 上传状态信息
     */
    interface UploadStatus {
        String getUuid();

        String getRepository();

        long getCurrentSize();

        long getTotalUploaded();

        String getStatus();

        String getProgress();
    }

    /**
     * 上传统计信息
     */
    interface UploadStats {
        long getActiveSessionCount();

        long getTotalUploadedSize();

        String getFormattedUploadedSize();
    }
}

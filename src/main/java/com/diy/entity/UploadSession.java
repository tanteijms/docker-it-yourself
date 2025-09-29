package com.diy.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 上传会话实体类
 * 管理blob分片上传的会话状态
 * 
 * @author registry
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadSession {

    /**
     * 上传会话UUID
     * 唯一标识一次上传会话
     */
    private String uuid;

    /**
     * 仓库名称
     */
    private String repository;

    /**
     * OSS临时文件key
     * 用于分片上传时的临时存储
     */
    private String ossTempKey;

    /**
     * 已上传字节数
     * 用于支持断点续传和进度追踪
     */
    private Long currentSize;

    /**
     * 开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 最后活动时间
     * 用于识别过期的会话
     */
    private LocalDateTime lastActivity;

    /**
     * 状态
     * ACTIVE: 活跃状态
     * COMPLETED: 已完成
     * EXPIRED: 已过期
     */
    private UploadStatus status;

    /**
     * 上传状态枚举
     */
    public enum UploadStatus {
        ACTIVE,
        COMPLETED,
        EXPIRED
    }
}

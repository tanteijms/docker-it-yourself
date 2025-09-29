package com.diy.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Blob实体类
 * 对应Docker镜像的二进制数据块（层文件、配置文件等）
 * 
 * @author registry
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Blob {

    /**
     * SHA256值，格式：sha256:abc123def456...
     * 作为主键，全局唯一标识一个blob
     */
    private String digest;

    /**
     * 文件大小（字节）
     */
    private Long size;

    /**
     * OSS对象存储key
     * 例如：blobs/ab/abc123def456.../data
     */
    private String ossObjectKey;

    /**
     * MIME类型
     * 默认：application/octet-stream
     */
    private String contentType;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}

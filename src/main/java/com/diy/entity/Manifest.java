package com.diy.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Manifest实体类
 * 对应Docker镜像的元数据信息
 * 
 * @author registry
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Manifest {

    /**
     * 自增主键
     */
    private Long id;

    /**
     * Manifest的SHA256值
     * 格式：sha256:abc123def456...
     */
    private String digest;

    /**
     * 仓库名称
     * 例如：nginx、hello-world、myapp/backend
     */
    private String repository;

    /**
     * 标签（可为空）
     * 当通过digest访问时为空
     * 当通过tag访问时有值，例如：latest、v1.0、stable
     */
    private String tag;

    /**
     * Manifest JSON内容
     * 包含镜像的所有层信息、配置信息等
     */
    private String content;

    /**
     * Content-Type
     * 支持的类型：
     * - application/vnd.docker.distribution.manifest.v2+json
     * - application/vnd.docker.distribution.manifest.list.v2+json
     */
    private String mediaType;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}

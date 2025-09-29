-- Docker Registry 数据库初始化脚本
CREATE DATABASE IF NOT EXISTS docker_registry DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE docker_registry;

-- 创建用户
CREATE USER IF NOT EXISTS 'diy'@'localhost' IDENTIFIED BY 'diy';
CREATE USER IF NOT EXISTS 'diy'@'%' IDENTIFIED BY 'diy';
GRANT ALL PRIVILEGES ON docker_registry.* TO 'diy'@'localhost';
GRANT ALL PRIVILEGES ON docker_registry.* TO 'diy'@'%';
FLUSH PRIVILEGES;

-- Blob存储表
CREATE TABLE IF NOT EXISTS blobs (
    digest VARCHAR(71) PRIMARY KEY COMMENT 'SHA256 digest，格式：sha256:xxx',
    size BIGINT NOT NULL COMMENT '文件大小（字节）',
    oss_object_key VARCHAR(500) NOT NULL COMMENT 'OSS对象存储key',
    content_type VARCHAR(100) DEFAULT 'application/octet-stream' COMMENT 'MIME类型',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Blob存储表';

-- Manifest存储表
CREATE TABLE IF NOT EXISTS manifests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    digest VARCHAR(71) NOT NULL UNIQUE COMMENT 'Manifest的SHA256值',
    repository VARCHAR(255) NOT NULL COMMENT '仓库名称',
    tag VARCHAR(128) COMMENT '标签（可为空，通过digest访问时为空）',
    content TEXT NOT NULL COMMENT 'Manifest JSON内容',
    media_type VARCHAR(100) NOT NULL COMMENT 'Content-Type',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_repo_tag (repository, tag),
    INDEX idx_repo_digest (repository, digest),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Manifest存储表';

-- 上传会话表
CREATE TABLE IF NOT EXISTS upload_sessions (
    uuid VARCHAR(36) PRIMARY KEY COMMENT '上传会话UUID',
    repository VARCHAR(255) NOT NULL COMMENT '仓库名称',
    oss_temp_key VARCHAR(500) COMMENT 'OSS临时文件key',
    current_size BIGINT DEFAULT 0 COMMENT '已上传字节数',
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    last_activity TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活动时间',
    status ENUM('ACTIVE', 'COMPLETED', 'EXPIRED') DEFAULT 'ACTIVE' COMMENT '状态',
    INDEX idx_status (status),
    INDEX idx_last_activity (last_activity),
    INDEX idx_repository (repository)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上传会话表';

-- 清理过期会话的定时任务（可选）
-- 可以通过应用程序或定时任务来清理过期数据

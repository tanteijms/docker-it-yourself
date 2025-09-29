package com.diy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Docker Registry配置属性
 * 
 * @author diy
 */
@Data
@Component
@ConfigurationProperties(prefix = "docker-registry")
public class RegistryProperties {

    /**
     * 上传配置
     */
    private Upload upload = new Upload();

    /**
     * 存储配置
     */
    private Storage storage = new Storage();

    /**
     * 上传相关配置
     */
    @Data
    public static class Upload {
        /**
         * 上传会话超时时间（秒）
         */
        private long sessionTimeout = 1800; // 30分钟

        /**
         * 清理间隔（秒）
         */
        private long cleanupInterval = 300; // 5分钟

        /**
         * 最大块大小（字节）
         */
        private long maxChunkSize = 10485760; // 10MB

        /**
         * 最大并发上传数
         */
        private int maxConcurrentUploads = 10;

        /**
         * 上传重试次数
         */
        private int retryCount = 3;
    }

    /**
     * 存储相关配置
     */
    @Data
    public static class Storage {
        /**
         * 存储类型（local/oss）
         */
        private String type = "oss";

        /**
         * 本地存储路径（当type=local时使用）
         */
        private String localPath = "./storage";

        /**
         * 是否启用存储校验
         */
        private boolean enableValidation = true;

        /**
         * 存储清理策略
         */
        private Cleanup cleanup = new Cleanup();
    }

    /**
     * 清理策略配置
     */
    @Data
    public static class Cleanup {
        /**
         * 是否启用自动清理
         */
        private boolean enabled = true;

        /**
         * 清理间隔（小时）
         */
        private long intervalHours = 24;

        /**
         * 保留天数
         */
        private long retentionDays = 30;
    }
}

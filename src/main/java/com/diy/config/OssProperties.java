package com.diy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OSS配置属性
 * 
 * @author diy
 */
@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {

    /**
     * 是否启用OSS
     */
    private boolean enabled = true;

    /**
     * OSS访问端点
     */
    private String endpoint;

    /**
     * 存储桶名称
     */
    private String bucketName;

    /**
     * 访问密钥ID
     */
    private String accessKeyId;

    /**
     * 访问密钥Secret
     */
    private String accessKeySecret;

    /**
     * Blob存储路径前缀
     */
    private String blobPrefix = "blobs/";

    /**
     * 临时文件路径前缀
     */
    private String tempPrefix = "temp/";

    /**
     * 连接超时时间（毫秒）
     */
    private int connectionTimeout = 10000;

    /**
     * Socket超时时间（毫秒）
     */
    private int socketTimeout = 30000;

    /**
     * 最大连接数
     */
    private int maxConnections = 100;

    /**
     * 验证配置是否完整
     */
    public boolean isConfigValid() {
        return enabled &&
                endpoint != null && !endpoint.trim().isEmpty() &&
                bucketName != null && !bucketName.trim().isEmpty() &&
                accessKeyId != null && !accessKeyId.trim().isEmpty() &&
                accessKeySecret != null && !accessKeySecret.trim().isEmpty();
    }
}

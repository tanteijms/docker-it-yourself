package com.diy.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.StaticCredentialsProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云OSS配置
 * 
 * @author diy
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "aliyun.oss", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OssConfig {

    @Autowired
    private OssProperties ossProperties;

    /**
     * 创建OSS客户端
     */
    @Bean
    public OSS ossClient() {
        if (!ossProperties.isConfigValid()) {
            throw new IllegalStateException("OSS configuration is invalid. " +
                    "Please check endpoint, bucketName, accessKeyId and accessKeySecret");
        }

        log.info("Initializing OSS client with endpoint: {}, bucket: {}",
                ossProperties.getEndpoint(), ossProperties.getBucketName());

        // 创建凭证提供者
        StaticCredentialsProvider credentialsProvider = CredentialsProviderFactory
                .newDefaultCredentialProvider(
                        ossProperties.getAccessKeyId(),
                        ossProperties.getAccessKeySecret());

        // 创建OSS客户端
        OSS ossClient = new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                credentialsProvider);

        // 验证连接
        try {
            if (ossClient.doesBucketExist(ossProperties.getBucketName())) {
                log.info("OSS client initialized successfully. Bucket '{}' exists.",
                        ossProperties.getBucketName());
            } else {
                log.warn("OSS bucket '{}' does not exist. Please create it first.",
                        ossProperties.getBucketName());
            }
        } catch (Exception e) {
            log.error("Failed to verify OSS connection: {}", e.getMessage());
            throw new RuntimeException("OSS connection verification failed", e);
        }

        return ossClient;
    }
}

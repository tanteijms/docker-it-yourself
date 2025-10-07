package com.diy.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.*;
import com.diy.config.OssProperties;
import com.diy.utils.DigestUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * 阿里云OSS存储服务
 * 封装所有OSS操作，提供统一的存储接口
 * 
 * @author diy
 */
@Slf4j
@Service
public class OssStorageService {

    @Autowired
    private OSS ossClient;

    @Autowired
    private OssProperties ossProperties;

    /**
     * 生成blob的OSS存储key
     * 路径格式：blobs/ab/abc123def456.../data
     * 
     * @param digest SHA256值
     * @return OSS存储key
     */
    public String generateBlobKey(String digest) {
        if (!DigestUtils.isValidDigest(digest)) {
            throw new IllegalArgumentException("Invalid digest format: " + digest);
        }

        // sha256:abc123def456... -> abc123def456...
        String hash = DigestUtils.extractHash(digest);

        // 使用前2位作为目录分层，避免单目录文件过多
        String prefix = hash.substring(0, 2);

        return String.format("%s%s/%s/data",
                ossProperties.getBlobPrefix(), prefix, hash);
    }

    /**
     * 生成临时文件的OSS key
     * 路径格式：temp/{uuid}.tmp
     * 
     * @param uuid 上传会话UUID
     * @return OSS临时文件key
     */
    public String generateTempKey(String uuid) {
        return ossProperties.getTempPrefix() + uuid + ".tmp";
    }

    /**
     * 上传数据到OSS
     * 
     * @param key           OSS对象key
     * @param inputStream   数据流
     * @param contentLength 内容长度
     * @throws IOException IO异常
     */
    public void putObject(String key, InputStream inputStream, long contentLength) throws IOException {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(contentLength);
            metadata.setContentType("application/octet-stream");

            PutObjectRequest request = new PutObjectRequest(
                    ossProperties.getBucketName(), key, inputStream, metadata);

            PutObjectResult result = ossClient.putObject(request);

            log.debug("Successfully uploaded object to OSS: key={}, etag={}",
                    key, result.getETag());

        } catch (Exception e) {
            log.error("Failed to upload object to OSS: key={}", key, e);
            throw new IOException("OSS upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * 获取OSS对象的输入流
     * 
     * @param key OSS对象key
     * @return 输入流
     * @throws IOException IO异常
     */
    public InputStream getObjectInputStream(String key) throws IOException {
        try {
            OSSObject ossObject = ossClient.getObject(ossProperties.getBucketName(), key);
            return ossObject.getObjectContent();
        } catch (Exception e) {
            log.error("Failed to get object from OSS: key={}", key, e);
            throw new IOException("OSS download failed: " + e.getMessage(), e);
        }
    }

    /**
     * 获取OSS对象元数据
     * 
     * @param key OSS对象key
     * @return 对象元数据
     * @throws IOException IO异常
     */
    public ObjectMetadata getObjectMetadata(String key) throws IOException {
        try {
            return ossClient.getObjectMetadata(ossProperties.getBucketName(), key);
        } catch (Exception e) {
            log.error("Failed to get object metadata from OSS: key={}", key, e);
            throw new IOException("OSS metadata access failed: " + e.getMessage(), e);
        }
    }

    /**
     * 检查OSS对象是否存在
     * 
     * @param key OSS对象key
     * @return 是否存在
     */
    public boolean doesObjectExist(String key) {
        try {
            return ossClient.doesObjectExist(ossProperties.getBucketName(), key);
        } catch (Exception e) {
            log.error("Failed to check object existence in OSS: key={}", key, e);
            return false;
        }
    }

    /**
     * 删除OSS对象
     * 
     * @param key OSS对象key
     */
    public void deleteObject(String key) {
        try {
            ossClient.deleteObject(ossProperties.getBucketName(), key);
            log.debug("Successfully deleted object from OSS: key={}", key);
        } catch (Exception e) {
            log.error("Failed to delete object from OSS: key={}", key, e);
            // 删除失败不抛异常，只记录日志
        }
    }

    /**
     * 复制OSS对象（临时文件移动到正式位置）
     * 
     * @param sourceKey 源key
     * @param destKey   目标key
     * @throws IOException IO异常
     */
    public void copyObject(String sourceKey, String destKey) throws IOException {
        try {
            CopyObjectRequest copyRequest = new CopyObjectRequest(
                    ossProperties.getBucketName(), sourceKey,
                    ossProperties.getBucketName(), destKey);

            CopyObjectResult result = ossClient.copyObject(copyRequest);

            log.debug("Successfully copied object in OSS: {} -> {}, etag={}",
                    sourceKey, destKey, result.getETag());

        } catch (Exception e) {
            log.error("Failed to copy object in OSS: {} -> {}", sourceKey, destKey, e);
            throw new IOException("OSS copy failed: " + e.getMessage(), e);
        }
    }

    /**
     * 追加写入OSS对象（用于分片上传）
     * 
     * @param key         OSS对象key
     * @param inputStream 数据流
     * @param position    追加位置
     * @return 追加结果
     * @throws IOException IO异常
     */
    public AppendObjectResult appendObject(String key, InputStream inputStream, long position) throws IOException {
        try {
            // 先读取所有数据到字节数组，确保准确的长度
            byte[] data = inputStream.readAllBytes();
            log.debug("Read {} bytes from input stream for OSS append", data.length);

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(data.length);

            // 用字节数组创建新的InputStream
            java.io.ByteArrayInputStream byteArrayInputStream = new java.io.ByteArrayInputStream(data);

            AppendObjectRequest appendRequest = new AppendObjectRequest(
                    ossProperties.getBucketName(), key, byteArrayInputStream, metadata);
            appendRequest.setPosition(position);

            AppendObjectResult result = ossClient.appendObject(appendRequest);

            log.debug("Successfully appended to object in OSS: key={}, position={}, nextPosition={}",
                    key, position, result.getNextPosition());

            return result;

        } catch (Exception e) {
            log.error("Failed to append to object in OSS: key={}, position={}", key, position, e);
            throw new IOException("OSS append failed: " + e.getMessage(), e);
        }
    }

    /**
     * 获取对象大小
     * 
     * @param key OSS对象key
     * @return 对象大小（字节）
     * @throws IOException IO异常
     */
    public long getObjectSize(String key) throws IOException {
        ObjectMetadata metadata = getObjectMetadata(key);
        return metadata.getContentLength();
    }

    /**
     * 生成预签名URL（用于直接访问，如果需要）
     * 
     * @param key        OSS对象key
     * @param expiration 过期时间（秒）
     * @return 预签名URL
     */
    public String generatePresignedUrl(String key, int expiration) {
        try {
            java.util.Date expTime = new java.util.Date(System.currentTimeMillis() + expiration * 1000L);
            return ossClient.generatePresignedUrl(ossProperties.getBucketName(), key, expTime).toString();
        } catch (Exception e) {
            log.error("Failed to generate presigned URL: key={}", key, e);
            return null;
        }
    }
}

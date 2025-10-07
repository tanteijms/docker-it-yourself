package com.diy.service.impl;

import com.diy.entity.Blob;
import com.diy.exception.BlobNotFoundException;
import com.diy.exception.InvalidDigestException;
import com.diy.mapper.BlobMapper;
import com.diy.service.BlobService;
import com.diy.service.OssStorageService;
import com.diy.utils.DigestUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.time.LocalDateTime;

/**
 * Blob业务服务实现类
 * 
 * @author diy
 */
@Slf4j
@Service
public class BlobServiceImpl implements BlobService {

    @Autowired
    private BlobMapper blobMapper;

    @Autowired
    private OssStorageService ossStorageService;

    @Override
    public Blob getBlobByDigest(String digest) {
        validateDigest(digest);

        Blob blob = blobMapper.findByDigest(digest);
        if (blob == null) {
            throw new BlobNotFoundException(digest);
        }

        // 验证OSS中文件是否存在
        if (!ossStorageService.doesObjectExist(blob.getOssObjectKey())) {
            log.warn("Blob exists in database but not in OSS: {}", digest);
            throw new BlobNotFoundException(digest, "Blob file not found in storage");
        }

        return blob;
    }

    @Override
    public boolean existsByDigest(String digest) {
        validateDigest(digest);

        try {
            getBlobByDigest(digest);
            return true;
        } catch (BlobNotFoundException e) {
            return false;
        }
    }

    @Override
    public InputStream getBlobInputStream(String digest) throws IOException {
        Blob blob = getBlobByDigest(digest);

        try {
            InputStream inputStream = ossStorageService.getObjectInputStream(blob.getOssObjectKey());
            log.debug("Retrieved blob input stream: digest={}, size={}", digest, blob.getSize());
            return inputStream;
        } catch (IOException e) {
            log.error("Failed to get blob input stream: digest={}", digest, e);
            throw e;
        }
    }

    @Override
    public long getBlobSize(String digest) {
        Blob blob = getBlobByDigest(digest);
        return blob.getSize();
    }

    @Override
    @Transactional
    public Blob createBlob(String digest, long size, String ossObjectKey, String contentType) {
        validateDigest(digest);

        if (size <= 0) {
            throw new IllegalArgumentException("Blob size must be positive: " + size);
        }

        if (ossObjectKey == null || ossObjectKey.trim().isEmpty()) {
            throw new IllegalArgumentException("OSS object key cannot be empty");
        }

        // 检查是否已存在
        if (blobMapper.existsByDigest(digest)) {
            log.info("Blob already exists, returning existing: {}", digest);
            return blobMapper.findByDigest(digest);
        }

        // 验证OSS中文件确实存在
        if (!ossStorageService.doesObjectExist(ossObjectKey)) {
            throw new IllegalStateException("OSS object does not exist: " + ossObjectKey);
        }

        // 创建新的blob记录
        Blob blob = new Blob();
        blob.setDigest(digest);
        blob.setSize(size);
        blob.setOssObjectKey(ossObjectKey);
        blob.setContentType(contentType != null ? contentType : "application/octet-stream");
        blob.setCreatedAt(LocalDateTime.now());

        int inserted = blobMapper.insert(blob);
        if (inserted <= 0) {
            throw new RuntimeException("Failed to insert blob record: " + digest);
        }

        log.info("Successfully created blob: digest={}, size={}, oss_key={}",
                digest, size, ossObjectKey);

        return blob;
    }

    @Override
    @Transactional
    public boolean deleteBlob(String digest) {
        validateDigest(digest);

        // 获取blob信息
        Blob blob;
        try {
            blob = getBlobByDigest(digest);
        } catch (BlobNotFoundException e) {
            log.warn("Attempted to delete non-existent blob: {}", digest);
            return false;
        }

        try {
            // 删除数据库记录
            int deleted = blobMapper.deleteByDigest(digest);
            if (deleted > 0) {
                // 删除OSS文件
                ossStorageService.deleteObject(blob.getOssObjectKey());
                log.info("Successfully deleted blob: digest={}, oss_key={}",
                        digest, blob.getOssObjectKey());
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to delete blob: {}", digest, e);
        }

        return false;
    }

    @Override
    public boolean validateBlobIntegrity(String digest) throws IOException {
        validateDigest(digest);

        Blob blob = getBlobByDigest(digest);

        try {
            // 验证OSS文件大小
            long actualSize = ossStorageService.getObjectSize(blob.getOssObjectKey());
            if (actualSize != blob.getSize()) {
                log.warn("Blob size mismatch: digest={}, expected={}, actual={}",
                        digest, blob.getSize(), actualSize);
                return false;
            }

            // 可以进一步验证SHA256值（性能开销较大，可选）
            // 这里只验证文件存在性和大小

            log.debug("Blob integrity validation passed: {}", digest);
            return true;

        } catch (IOException e) {
            log.error("Blob integrity validation failed: {}", digest, e);
            throw e;
        }
    }

    @Override
    public BlobStats getBlobStats() {
        try {
            long totalCount = blobMapper.countAll();
            long totalSize = blobMapper.sumSize();

            return new BlobStatsImpl(totalCount, totalSize);
        } catch (Exception e) {
            log.error("Failed to get blob stats", e);
            return new BlobStatsImpl(0, 0);
        }
    }

    /**
     * 验证digest格式
     */
    private void validateDigest(String digest) {
        if (!DigestUtils.isValidDigest(digest)) {
            throw new InvalidDigestException(digest);
        }
    }

    /**
     * Blob统计信息实现类
     */
    @Data
    @AllArgsConstructor
    private static class BlobStatsImpl implements BlobStats {
        private final long totalCount;
        private final long totalSize;

        @Override
        public String getFormattedSize() {
            return formatFileSize(totalSize);
        }

        private String formatFileSize(long size) {
            if (size <= 0)
                return "0 B";

            final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
            int digitGroups = (int) (Math.log10(size) / Math.log10(1024));

            DecimalFormat df = new DecimalFormat("#,##0.#");
            return df.format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
        }
    }
}

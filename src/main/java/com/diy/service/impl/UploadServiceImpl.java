package com.diy.service.impl;

import com.aliyun.oss.model.AppendObjectResult;
import com.diy.config.RegistryProperties;
import com.diy.entity.Blob;
import com.diy.entity.UploadSession;
import com.diy.exception.InvalidDigestException;
import com.diy.exception.UploadSessionNotFoundException;
import com.diy.mapper.UploadSessionMapper;
import com.diy.service.BlobService;
import com.diy.service.OssStorageService;
import com.diy.service.UploadService;
import com.diy.utils.DigestUtils;
import com.diy.utils.RangeUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 上传业务服务实现类
 * 
 * @author diy
 */
@Slf4j
@Service
public class UploadServiceImpl implements UploadService {

    @Autowired
    private UploadSessionMapper uploadSessionMapper;

    @Autowired
    private OssStorageService ossStorageService;

    @Autowired
    private BlobService blobService;

    @Autowired
    private RegistryProperties registryProperties;

    @Override
    @Transactional
    public UploadSession startUploadSession(String repository) {
        if (repository == null || repository.trim().isEmpty()) {
            throw new IllegalArgumentException("Repository name cannot be empty");
        }

        // 生成唯一的会话UUID
        String uuid = UUID.randomUUID().toString();

        // 生成临时文件key
        String ossTempKey = ossStorageService.generateTempKey(uuid);

        // 创建上传会话
        UploadSession session = new UploadSession();
        session.setUuid(uuid);
        session.setRepository(repository);
        session.setOssTempKey(ossTempKey);
        session.setCurrentSize(0L);
        session.setStartedAt(LocalDateTime.now());
        session.setLastActivity(LocalDateTime.now());
        session.setStatus(UploadSession.UploadStatus.ACTIVE);

        int inserted = uploadSessionMapper.insert(session);
        if (inserted <= 0) {
            throw new RuntimeException("Failed to create upload session");
        }

        log.info("Started new upload session: uuid={}, repository={}, temp_key={}",
                uuid, repository, ossTempKey);

        return session;
    }

    @Override
    public UploadSession getUploadSession(String uuid) {
        if (uuid == null || uuid.trim().isEmpty()) {
            throw new IllegalArgumentException("Session UUID cannot be empty");
        }

        UploadSession session = uploadSessionMapper.findByUuid(uuid);
        if (session == null) {
            throw new UploadSessionNotFoundException(uuid);
        }

        // 检查会话是否过期
        if (isSessionExpired(session)) {
            log.warn("Upload session has expired: {}", uuid);
            // 标记为过期但不立即删除，由定时任务清理
            uploadSessionMapper.updateStatus(uuid, "EXPIRED", LocalDateTime.now());
            throw new UploadSessionNotFoundException(uuid, "Upload session has expired");
        }

        return session;
    }

    @Override
    @Transactional
    public UploadSession uploadChunk(String uuid, InputStream inputStream, String expectedRange) throws IOException {
        UploadSession session = getUploadSession(uuid);

        // 解析Range头
        RangeUtils.RangeInfo rangeInfo = RangeUtils.parseContentRange(expectedRange);
        if (!rangeInfo.isValid()) {
            throw new IllegalArgumentException("Invalid range format: " + expectedRange);
        }

        // 验证范围连续性
        if (rangeInfo.getStart() != session.getCurrentSize()) {
            throw new IllegalArgumentException(String.format(
                    "Range mismatch: expected start=%d, actual start=%d",
                    session.getCurrentSize(), rangeInfo.getStart()));
        }

        try {
            // 追加数据到临时文件
            long expectedBytes = rangeInfo.getLength();
            long position = session.getCurrentSize();

            // 使用OSS的追加写入功能
            AppendObjectResult appendResult = ossStorageService.appendObject(session.getOssTempKey(), inputStream,
                    position);

            // 获取实际写入的字节数
            long actualNewSize = appendResult.getNextPosition();
            long actualBytesWritten = actualNewSize - position;

            // 验证写入的字节数是否符合预期
            if (actualBytesWritten != expectedBytes) {
                log.warn("Bytes written mismatch: expected={}, actual={}", expectedBytes, actualBytesWritten);
            }

            // 更新会话状态（使用实际的新大小）
            session.setCurrentSize(actualNewSize);
            session.setLastActivity(LocalDateTime.now());

            uploadSessionMapper.updateProgress(uuid, actualNewSize, LocalDateTime.now());

            log.debug("Uploaded chunk: uuid={}, range={}, expected_bytes={}, actual_bytes={}, new_size={}",
                    uuid, expectedRange, expectedBytes, actualBytesWritten, actualNewSize);

            return session;

        } catch (IOException e) {
            log.error("Failed to upload chunk: uuid={}, range={}", uuid, expectedRange, e);
            throw e;
        }
    }

    @Override
    @Transactional
    public Blob completeUpload(String uuid, String expectedDigest) throws IOException {
        UploadSession session = getUploadSession(uuid);

        if (!DigestUtils.isValidDigest(expectedDigest)) {
            throw new InvalidDigestException(expectedDigest);
        }

        try {
            // 验证上传文件的完整性
            String actualDigest = calculateTempFileDigest(session.getOssTempKey());
            if (!expectedDigest.equals(actualDigest)) {
                throw new InvalidDigestException(expectedDigest,
                        "Digest mismatch: expected=" + expectedDigest + ", actual=" + actualDigest);
            }

            // 生成最终的blob存储key
            String finalBlobKey = ossStorageService.generateBlobKey(expectedDigest);

            // 移动临时文件到最终位置
            ossStorageService.copyObject(session.getOssTempKey(), finalBlobKey);

            // 创建blob记录
            Blob blob = blobService.createBlob(
                    expectedDigest,
                    session.getCurrentSize(),
                    finalBlobKey,
                    "application/octet-stream");

            // 清理临时文件
            ossStorageService.deleteObject(session.getOssTempKey());

            // 标记会话为完成
            uploadSessionMapper.updateStatus(uuid, "COMPLETED", LocalDateTime.now());

            // 删除完成的会话记录
            uploadSessionMapper.deleteByUuid(uuid);

            log.info("Successfully completed upload: uuid={}, digest={}, size={}",
                    uuid, expectedDigest, session.getCurrentSize());

            return blob;

        } catch (Exception e) {
            log.error("Failed to complete upload: uuid={}, expected_digest={}", uuid, expectedDigest, e);
            // 保留会话和临时文件，供调试或重试
            throw e;
        }
    }

    @Override
    @Transactional
    public boolean cancelUploadSession(String uuid) {
        try {
            UploadSession session = getUploadSession(uuid);

            // 删除临时文件
            if (session.getOssTempKey() != null) {
                ossStorageService.deleteObject(session.getOssTempKey());
            }

            // 删除会话记录
            int deleted = uploadSessionMapper.deleteByUuid(uuid);

            log.info("Cancelled upload session: uuid={}, temp_key={}",
                    uuid, session.getOssTempKey());

            return deleted > 0;

        } catch (UploadSessionNotFoundException e) {
            log.warn("Attempted to cancel non-existent upload session: {}", uuid);
            return false;
        } catch (Exception e) {
            log.error("Failed to cancel upload session: {}", uuid, e);
            return false;
        }
    }

    @Override
    public boolean isUploadSessionValid(String uuid) {
        try {
            getUploadSession(uuid);
            return true;
        } catch (UploadSessionNotFoundException e) {
            return false;
        }
    }

    @Override
    public UploadStatus getUploadStatus(String uuid) {
        UploadSession session = getUploadSession(uuid);
        return new UploadStatusImpl(session);
    }

    @Override
    @Scheduled(fixedDelayString = "#{@registryProperties.upload.cleanupInterval * 1000}")
    public int cleanupExpiredSessions() {
        try {
            LocalDateTime expireTime = LocalDateTime.now()
                    .minusSeconds(registryProperties.getUpload().getSessionTimeout());

            // 获取过期会话（包含临时文件信息）
            List<UploadSession> expiredSessions = uploadSessionMapper.findExpiredActiveSessions(expireTime);

            int cleanedCount = 0;
            for (UploadSession session : expiredSessions) {
                try {
                    // 删除临时文件
                    if (session.getOssTempKey() != null) {
                        ossStorageService.deleteObject(session.getOssTempKey());
                    }
                    cleanedCount++;
                } catch (Exception e) {
                    log.warn("Failed to delete temp file for expired session: {}",
                            session.getUuid(), e);
                }
            }

            // 批量删除过期会话记录
            int deletedSessions = uploadSessionMapper.deleteExpiredSessions(expireTime);

            if (deletedSessions > 0) {
                log.info("Cleaned up expired upload sessions: count={}, temp_files_deleted={}",
                        deletedSessions, cleanedCount);
            }

            return deletedSessions;

        } catch (Exception e) {
            log.error("Failed to cleanup expired sessions", e);
            return 0;
        }
    }

    @Override
    public UploadStats getUploadStats() {
        try {
            long activeCount = uploadSessionMapper.countActiveSessions();
            // 可以添加更多统计信息
            return new UploadStatsImpl(activeCount, 0L);
        } catch (Exception e) {
            log.error("Failed to get upload stats", e);
            return new UploadStatsImpl(0L, 0L);
        }
    }

    /**
     * 检查会话是否过期
     */
    private boolean isSessionExpired(UploadSession session) {
        if (session.getStatus() != UploadSession.UploadStatus.ACTIVE) {
            return true;
        }

        LocalDateTime expireTime = LocalDateTime.now()
                .minusSeconds(registryProperties.getUpload().getSessionTimeout());

        return session.getLastActivity().isBefore(expireTime);
    }

    /**
     * 计算临时文件的SHA256值
     */
    private String calculateTempFileDigest(String tempKey) throws IOException {
        try (InputStream inputStream = ossStorageService.getObjectInputStream(tempKey)) {
            return DigestUtils.calculateSHA256(inputStream);
        }
    }

    /**
     * 上传状态实现类
     */
    @Data
    @AllArgsConstructor
    private static class UploadStatusImpl implements UploadStatus {
        private final UploadSession session;

        @Override
        public String getUuid() {
            return session.getUuid();
        }

        @Override
        public String getRepository() {
            return session.getRepository();
        }

        @Override
        public long getCurrentSize() {
            return session.getCurrentSize();
        }

        @Override
        public long getTotalUploaded() {
            return session.getCurrentSize();
        }

        @Override
        public String getStatus() {
            return session.getStatus().toString();
        }

        @Override
        public String getProgress() {
            // 简单的进度显示，可以根据需要扩展
            return String.format("%d bytes uploaded", session.getCurrentSize());
        }
    }

    /**
     * 上传统计实现类
     */
    @Data
    @AllArgsConstructor
    private static class UploadStatsImpl implements UploadStats {
        private final long activeSessionCount;
        private final long totalUploadedSize;

        @Override
        public String getFormattedUploadedSize() {
            return formatFileSize(totalUploadedSize);
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

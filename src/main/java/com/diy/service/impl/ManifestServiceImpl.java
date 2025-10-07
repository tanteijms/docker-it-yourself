package com.diy.service.impl;

import com.diy.config.WebConfig;
import com.diy.dto.ManifestDto;
import com.diy.entity.Manifest;
import com.diy.exception.BlobNotFoundException;
import com.diy.exception.ManifestNotFoundException;
import com.diy.mapper.ManifestMapper;
import com.diy.service.BlobService;
import com.diy.service.ManifestService;
import com.diy.utils.DigestUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manifest业务服务实现类
 * 
 * @author diy
 */
@Slf4j
@Service
public class ManifestServiceImpl implements ManifestService {

    @Autowired
    private ManifestMapper manifestMapper;

    @Autowired
    private BlobService blobService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Manifest getManifest(String repository, String reference) {
        validateRepository(repository);
        validateReference(reference);

        Manifest manifest;

        if (isDigestReference(reference)) {
            // 通过digest获取
            manifest = manifestMapper.findByRepositoryAndDigest(repository, reference);
        } else {
            // 通过tag获取，返回最新的manifest
            List<Manifest> manifests = manifestMapper.findByRepositoryAndTag(repository, reference);
            manifest = manifests.isEmpty() ? null : manifests.get(0);
        }

        if (manifest == null) {
            throw new ManifestNotFoundException(repository, reference);
        }

        return manifest;
    }

    @Override
    public boolean existsManifest(String repository, String reference) {
        try {
            getManifest(repository, reference);
            return true;
        } catch (ManifestNotFoundException e) {
            return false;
        }
    }

    @Override
    @Transactional
    public Manifest putManifest(String repository, String reference, String manifestContent, String mediaType) {
        validateRepository(repository);
        validateReference(reference);
        validateManifestContent(manifestContent);
        validateMediaType(mediaType);

        // 解析manifest内容
        ManifestDto manifestDto;
        try {
            manifestDto = objectMapper.readValue(manifestContent, ManifestDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid manifest JSON format", e);
        }

        // 验证依赖的blob是否存在
        ManifestValidationResult validation = validateManifestDependencies(manifestDto);
        if (!validation.isValid()) {
            throw new BlobNotFoundException("",
                    "Missing required blobs: " + String.join(", ", validation.getMissingBlobs()));
        }

        // 计算manifest的digest
        String manifestDigest = calculateManifestDigest(manifestContent);

        // 检查是否已存在相同的manifest
        if (manifestMapper.existsByRepositoryAndDigest(repository, manifestDigest)) {
            log.info("Manifest already exists: repository={}, digest={}", repository, manifestDigest);
            return manifestMapper.findByRepositoryAndDigest(repository, manifestDigest);
        }

        // 创建新的manifest记录
        Manifest manifest = new Manifest();
        manifest.setRepository(repository);
        manifest.setDigest(manifestDigest);
        manifest.setContent(manifestContent);
        manifest.setMediaType(mediaType);
        manifest.setCreatedAt(LocalDateTime.now());

        // 如果reference不是digest，则设置为tag
        if (!isDigestReference(reference)) {
            manifest.setTag(reference);
        }

        int inserted = manifestMapper.insert(manifest);
        if (inserted <= 0) {
            throw new RuntimeException("Failed to insert manifest record");
        }

        log.info("Successfully created manifest: repository={}, reference={}, digest={}, media_type={}",
                repository, reference, manifestDigest, mediaType);

        return manifest;
    }

    @Override
    @Transactional
    public boolean deleteManifest(String repository, String reference) {
        validateRepository(repository);
        validateReference(reference);

        try {
            Manifest manifest = getManifest(repository, reference);

            int deleted;
            if (isDigestReference(reference)) {
                deleted = manifestMapper.deleteByDigest(reference);
            } else {
                deleted = manifestMapper.deleteByRepositoryAndTag(repository, reference);
            }

            if (deleted > 0) {
                log.info("Successfully deleted manifest: repository={}, reference={}, digest={}",
                        repository, reference, manifest.getDigest());
                return true;
            }

        } catch (ManifestNotFoundException e) {
            log.warn("Attempted to delete non-existent manifest: repository={}, reference={}",
                    repository, reference);
        }

        return false;
    }

    @Override
    public List<String> getRepositoryTags(String repository) {
        validateRepository(repository);

        try {
            return manifestMapper.findTagsByRepository(repository);
        } catch (Exception e) {
            log.error("Failed to get repository tags: repository={}", repository, e);
            return new ArrayList<>();
        }
    }

    @Override
    public String buildManifestList(String repository, String tag) {
        validateRepository(repository);

        if (tag == null || tag.trim().isEmpty()) {
            throw new IllegalArgumentException("Tag cannot be empty");
        }

        // 获取该tag下的所有manifest
        List<Manifest> manifests = manifestMapper.findByRepositoryAndTag(repository, tag);

        if (manifests.isEmpty()) {
            throw new ManifestNotFoundException(repository, tag);
        }

        // 构建manifest list
        try {
            ManifestListDto manifestList = new ManifestListDto();
            manifestList.setSchemaVersion(2);
            manifestList.setMediaType(WebConfig.MANIFEST_LIST_V2_MEDIA_TYPE);

            List<ManifestListDto.ManifestReferenceDto> manifestRefs = manifests.stream()
                    .map(this::convertToManifestReference)
                    .collect(Collectors.toList());

            manifestList.setManifests(manifestRefs);

            return objectMapper.writeValueAsString(manifestList);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build manifest list JSON", e);
        }
    }

    @Override
    public ManifestValidationResult validateManifestDependencies(ManifestDto manifestDto) {
        List<String> missingBlobs = new ArrayList<>();

        try {
            // 检查config blob（如果存在且不是内联数据）
            if (manifestDto.getConfig() != null &&
                    manifestDto.getConfig().getDigest() != null &&
                    manifestDto.getConfig().getData() == null) {

                if (!blobService.existsByDigest(manifestDto.getConfig().getDigest())) {
                    missingBlobs.add(manifestDto.getConfig().getDigest());
                }
            }

            // 检查所有层的blob
            if (manifestDto.getLayers() != null) {
                for (ManifestDto.LayerDto layer : manifestDto.getLayers()) {
                    if (layer.getDigest() != null &&
                            !blobService.existsByDigest(layer.getDigest())) {
                        missingBlobs.add(layer.getDigest());
                    }
                }
            }

            boolean isValid = missingBlobs.isEmpty();
            String errorMessage = isValid ? null : "Missing required blobs: " + String.join(", ", missingBlobs);

            return new ManifestValidationResultImpl(isValid, missingBlobs, errorMessage);

        } catch (Exception e) {
            log.error("Failed to validate manifest dependencies", e);
            return new ManifestValidationResultImpl(false, missingBlobs, "Validation error: " + e.getMessage());
        }
    }

    @Override
    public String calculateManifestDigest(String manifestContent) {
        if (manifestContent == null || manifestContent.trim().isEmpty()) {
            throw new IllegalArgumentException("Manifest content cannot be empty");
        }

        return DigestUtils.calculateSHA256(manifestContent);
    }

    @Override
    public boolean isDigestReference(String reference) {
        return DigestUtils.isValidDigest(reference);
    }

    @Override
    public ManifestStats getManifestStats(String repository) {
        try {
            // 可以根据需要实现更详细的统计
            // 这里提供基础实现
            long totalCount = repository != null ? manifestMapper.findByRepository(repository).size() : 0; // 需要添加全局统计的mapper方法

            return new ManifestStatsImpl(totalCount, 0L, 0L);

        } catch (Exception e) {
            log.error("Failed to get manifest stats", e);
            return new ManifestStatsImpl(0L, 0L, 0L);
        }
    }

    /**
     * 验证仓库名
     */
    private void validateRepository(String repository) {
        if (repository == null || repository.trim().isEmpty()) {
            throw new IllegalArgumentException("Repository name cannot be empty");
        }
    }

    /**
     * 验证引用
     */
    private void validateReference(String reference) {
        if (reference == null || reference.trim().isEmpty()) {
            throw new IllegalArgumentException("Reference cannot be empty");
        }
    }

    /**
     * 验证manifest内容
     */
    private void validateManifestContent(String manifestContent) {
        if (manifestContent == null || manifestContent.trim().isEmpty()) {
            throw new IllegalArgumentException("Manifest content cannot be empty");
        }
    }

    /**
     * 验证媒体类型
     */
    private void validateMediaType(String mediaType) {
        if (mediaType == null || mediaType.trim().isEmpty()) {
            throw new IllegalArgumentException("Media type cannot be empty");
        }

        // 检查是否为支持的类型
        if (!WebConfig.MANIFEST_V2_MEDIA_TYPE.equals(mediaType) &&
                !WebConfig.MANIFEST_LIST_V2_MEDIA_TYPE.equals(mediaType) &&
                !WebConfig.OCI_MANIFEST_MEDIA_TYPE.equals(mediaType) &&
                !WebConfig.OCI_INDEX_MEDIA_TYPE.equals(mediaType)) {

            log.warn("Unsupported media type: {}", mediaType);
            // 不抛异常，允许其他类型，只记录警告
        }
    }

    /**
     * 转换Manifest为ManifestReference
     */
    private ManifestListDto.ManifestReferenceDto convertToManifestReference(Manifest manifest) {
        ManifestListDto.ManifestReferenceDto ref = new ManifestListDto.ManifestReferenceDto();
        ref.setDigest(manifest.getDigest());
        ref.setMediaType(manifest.getMediaType());
        ref.setSize((long) manifest.getContent().length()); // 简化的大小计算

        // 可以根据需要添加platform信息
        ManifestListDto.PlatformDto platform = new ManifestListDto.PlatformDto();
        platform.setArchitecture("amd64"); // 默认值
        platform.setOs("linux"); // 默认值
        ref.setPlatform(platform);

        return ref;
    }

    /**
     * Manifest验证结果实现类
     */
    @Data
    @AllArgsConstructor
    private static class ManifestValidationResultImpl implements ManifestValidationResult {
        private final boolean valid;
        private final List<String> missingBlobs;
        private final String errorMessage;
    }

    /**
     * Manifest统计实现类
     */
    @Data
    @AllArgsConstructor
    private static class ManifestStatsImpl implements ManifestStats {
        private final long totalCount;
        private final long repositoryCount;
        private final long tagCount;
    }

    /**
     * Manifest List DTO（内部使用）
     */
    @Data
    private static class ManifestListDto {
        private Integer schemaVersion;
        private String mediaType;
        private List<ManifestReferenceDto> manifests;

        @Data
        public static class ManifestReferenceDto {
            private String digest;
            private String mediaType;
            private Long size;
            private PlatformDto platform;
        }

        @Data
        public static class PlatformDto {
            private String architecture;
            private String os;
        }
    }
}

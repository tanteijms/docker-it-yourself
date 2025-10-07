package com.diy.controller;

import com.diy.config.WebConfig;
import com.diy.entity.Manifest;
import com.diy.exception.UnsupportedMediaTypeException;
import com.diy.service.ManifestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Manifest相关API控制器
 * 处理Docker镜像元数据的操作
 * 
 * @author diy
 */
@Slf4j
@RestController
@RequestMapping("/v2/{name}/manifests")
public class ManifestController {

    @Autowired
    private ManifestService manifestService;

    /**
     * GET /v2/{name}/manifests/{reference} - 获取manifest
     * 根据tag或digest获取manifest内容
     * 
     * @param name      仓库名
     * @param reference 引用（tag或digest）
     * @param accept    Accept头，指定期望的媒体类型
     * @return manifest JSON内容
     */
    @GetMapping("/{reference}")
    public ResponseEntity<String> getManifest(
            @PathVariable String name,
            @PathVariable String reference,
            @RequestHeader(value = "Accept", required = false) String accept) {

        log.debug("Get manifest request: repository={}, reference={}, accept={}", name, reference, accept);

        if (manifestService.isDigestReference(reference)) {
            // 通过digest获取具体的manifest
            Manifest manifest = manifestService.getManifest(name, reference);

            return ResponseEntity.ok()
                    .header("Content-Type", manifest.getMediaType())
                    .header("Docker-Content-Digest", manifest.getDigest())
                    .body(manifest.getContent());

        } else {
            // 通过tag获取，需要判断返回manifest还是manifest list
            if (isManifestListRequest(accept)) {
                // 返回manifest list
                String manifestListContent = manifestService.buildManifestList(name, reference);

                return ResponseEntity.ok()
                        .header("Content-Type", WebConfig.MANIFEST_LIST_V2_MEDIA_TYPE)
                        .body(manifestListContent);
            } else {
                // 返回最新的manifest
                Manifest manifest = manifestService.getManifest(name, reference);

                return ResponseEntity.ok()
                        .header("Content-Type", manifest.getMediaType())
                        .header("Docker-Content-Digest", manifest.getDigest())
                        .body(manifest.getContent());
            }
        }
    }

    /**
     * HEAD /v2/{name}/manifests/{reference} - 检查manifest是否存在
     * 返回manifest的元数据信息，不返回内容
     * 
     * @param name      仓库名
     * @param reference 引用（tag或digest）
     * @param accept    Accept头，指定期望的媒体类型
     * @return 200 OK（存在）或 404 Not Found（不存在）
     */
    @RequestMapping(value = "/{reference}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headManifest(
            @PathVariable String name,
            @PathVariable String reference,
            @RequestHeader(value = "Accept", required = false) String accept) {

        log.debug("Head manifest request: repository={}, reference={}", name, reference);

        if (manifestService.existsManifest(name, reference)) {
            Manifest manifest = manifestService.getManifest(name, reference);

            String contentType = manifest.getMediaType();
            if (!manifestService.isDigestReference(reference) && isManifestListRequest(accept)) {
                contentType = WebConfig.MANIFEST_LIST_V2_MEDIA_TYPE;
            }

            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .header("Docker-Content-Digest", manifest.getDigest())
                    .build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * PUT /v2/{name}/manifests/{reference} - 上传manifest
     * 创建或更新manifest
     * 
     * @param name            仓库名
     * @param reference       引用（通常是tag）
     * @param contentType     Content-Type头，指定manifest类型
     * @param manifestContent manifest JSON内容
     * @return 201 Created，Location头包含manifest的访问URL
     */
    @PutMapping("/{reference}")
    public ResponseEntity<Void> putManifest(
            @PathVariable String name,
            @PathVariable String reference,
            @RequestHeader("Content-Type") String contentType,
            @RequestBody String manifestContent) {

        log.debug("Put manifest request: repository={}, reference={}, content_type={}",
                name, reference, contentType);

        // 验证Content-Type
        if (!isSupportedManifestType(contentType)) {
            log.warn("Unsupported manifest content type: {}", contentType);
            throw new UnsupportedMediaTypeException(contentType);
        }

        // 创建或更新manifest
        Manifest manifest = manifestService.putManifest(name, reference, manifestContent, contentType);

        // 构建manifest访问URL
        String manifestUrl = String.format("/v2/%s/manifests/%s", name, manifest.getDigest());

        log.info("Manifest uploaded successfully: repository={}, reference={}, digest={}",
                name, reference, manifest.getDigest());

        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create(manifestUrl))
                .header("Docker-Content-Digest", manifest.getDigest())
                .build();
    }

    /**
     * DELETE /v2/{name}/manifests/{reference} - 删除manifest
     * 根据tag或digest删除manifest
     * 
     * @param name      仓库名
     * @param reference 引用（tag或digest）
     * @return 202 Accepted（成功）或 404 Not Found（不存在）
     */
    @DeleteMapping("/{reference}")
    public ResponseEntity<Void> deleteManifest(
            @PathVariable String name,
            @PathVariable String reference) {

        log.debug("Delete manifest request: repository={}, reference={}", name, reference);

        boolean deleted = manifestService.deleteManifest(name, reference);

        if (deleted) {
            log.info("Manifest deleted successfully: repository={}, reference={}", name, reference);
            return ResponseEntity.accepted().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 判断是否为manifest list请求
     * 
     * @param accept Accept头
     * @return 是否请求manifest list
     */
    private boolean isManifestListRequest(String accept) {
        if (accept == null) {
            return false;
        }

        return accept.contains(WebConfig.MANIFEST_LIST_V2_MEDIA_TYPE) ||
                accept.contains(WebConfig.OCI_INDEX_MEDIA_TYPE) ||
                accept.contains("application/vnd.docker.distribution.manifest.list");
    }

    /**
     * 检查是否为支持的manifest类型
     * 
     * @param contentType Content-Type
     * @return 是否支持
     */
    private boolean isSupportedManifestType(String contentType) {
        return WebConfig.MANIFEST_V2_MEDIA_TYPE.equals(contentType) ||
                WebConfig.MANIFEST_LIST_V2_MEDIA_TYPE.equals(contentType) ||
                WebConfig.OCI_MANIFEST_MEDIA_TYPE.equals(contentType) ||
                WebConfig.OCI_INDEX_MEDIA_TYPE.equals(contentType) ||
                contentType.contains("application/vnd.docker.distribution.manifest") ||
                contentType.contains("application/vnd.oci.image.manifest");
    }
}

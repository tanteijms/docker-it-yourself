package com.diy.controller;

import com.diy.entity.Blob;
import com.diy.entity.UploadSession;
import com.diy.service.UploadService;
import com.diy.utils.RangeUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;

/**
 * 上传相关API控制器
 * 处理Docker镜像层数据的分片上传
 * 
 * @author diy
 */
@Slf4j
@RestController
@RequestMapping("/v2/{name}/blobs/uploads")
public class UploadController {

    @Autowired
    private UploadService uploadService;

    /**
     * POST /v2/{name}/blobs/uploads/ - 开始上传会话
     * 初始化一个新的blob上传会话
     * 
     * @param name 仓库名
     * @return 202 Accepted，Location头包含上传URL
     */
    @PostMapping("/")
    public ResponseEntity<Void> startUpload(@PathVariable String name) {
        log.debug("Start upload request: repository={}", name);

        // 创建新的上传会话
        UploadSession session = uploadService.startUploadSession(name);

        // 构建上传URL
        String uploadUrl = String.format("/v2/%s/blobs/uploads/%s", name, session.getUuid());

        log.info("Started upload session: repository={}, uuid={}", name, session.getUuid());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Location", uploadUrl)
                .header("Range", RangeUtils.buildRangeResponse(0, 0, 0))
                .header("Docker-Upload-UUID", session.getUuid())
                .build();
    }

    /**
     * PATCH /v2/{name}/blobs/uploads/{uuid} - 分片上传数据
     * 上传数据块到指定的上传会话
     * 
     * @param name         仓库名
     * @param uuid         上传会话UUID
     * @param contentRange Content-Range头（格式：start-end）
     * @param request      HTTP请求，用于获取输入流
     * @return 202 Accepted，Range头显示当前进度
     */
    @PatchMapping("/{uuid}")
    public ResponseEntity<Void> uploadChunk(
            @PathVariable String name,
            @PathVariable String uuid,
            @RequestHeader("Content-Range") String contentRange,
            HttpServletRequest request) throws IOException {

        log.debug("Upload chunk request: repository={}, uuid={}, range={}", name, uuid, contentRange);

        // 验证会话存在
        UploadSession session = uploadService.getUploadSession(uuid);

        // 验证仓库名匹配
        if (!name.equals(session.getRepository())) {
            return ResponseEntity.badRequest().build();
        }

        // 上传数据块
        UploadSession updatedSession = uploadService.uploadChunk(
                uuid, request.getInputStream(), contentRange);

        // 构建响应
        String uploadUrl = String.format("/v2/%s/blobs/uploads/%s", name, uuid);
        String rangeHeader = RangeUtils.buildRangeResponse(0, updatedSession.getCurrentSize() - 1,
                updatedSession.getCurrentSize());

        log.debug("Chunk uploaded: uuid={}, current_size={}", uuid, updatedSession.getCurrentSize());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Location", uploadUrl)
                .header("Range", rangeHeader)
                .header("Docker-Upload-UUID", uuid)
                .build();
    }

    /**
     * PUT /v2/{name}/blobs/uploads/{uuid} - 完成上传
     * 完成上传并验证blob的完整性
     * 
     * @param name    仓库名
     * @param uuid    上传会话UUID
     * @param digest  期望的SHA256值
     * @param request HTTP请求，可能包含最后的数据块
     * @return 201 Created，Location头包含blob的访问URL
     */
    @PutMapping("/{uuid}")
    public ResponseEntity<Void> completeUpload(
            @PathVariable String name,
            @PathVariable String uuid,
            @RequestParam("digest") String digest,
            HttpServletRequest request) throws IOException {

        log.debug("Complete upload request: repository={}, uuid={}, digest={}", name, uuid, digest);

        // 验证会话存在
        UploadSession session = uploadService.getUploadSession(uuid);

        // 验证仓库名匹配
        if (!name.equals(session.getRepository())) {
            return ResponseEntity.badRequest().build();
        }

        // 处理可能的最后数据块
        if (request.getContentLength() > 0) {
            String contentRange = request.getHeader("Content-Range");
            if (contentRange == null) {
                // 如果没有Content-Range头，假设是从当前位置开始的数据
                long currentSize = session.getCurrentSize();
                contentRange = String.format("%d-%d", currentSize, currentSize + request.getContentLength() - 1);
            }

            uploadService.uploadChunk(uuid, request.getInputStream(), contentRange);
            log.debug("Final chunk uploaded: uuid={}, content_length={}", uuid, request.getContentLength());
        }

        // 完成上传并验证
        Blob blob = uploadService.completeUpload(uuid, digest);

        // 构建blob访问URL
        String blobUrl = String.format("/v2/%s/blobs/%s", name, digest);

        log.info("Upload completed successfully: repository={}, uuid={}, digest={}, size={}",
                name, uuid, digest, blob.getSize());

        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create(blobUrl))
                .header("Docker-Content-Digest", digest)
                .build();
    }

    /**
     * GET /v2/{name}/blobs/uploads/{uuid} - 获取上传状态
     * 查询当前上传会话的状态和进度
     * 
     * @param name 仓库名
     * @param uuid 上传会话UUID
     * @return 202 Accepted，Range头显示当前进度
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<Void> getUploadStatus(
            @PathVariable String name,
            @PathVariable String uuid) {

        log.debug("Get upload status request: repository={}, uuid={}", name, uuid);

        // 获取上传会话状态
        UploadService.UploadStatus status = uploadService.getUploadStatus(uuid);

        // 验证仓库名匹配
        if (!name.equals(status.getRepository())) {
            return ResponseEntity.badRequest().build();
        }

        // 构建响应
        String uploadUrl = String.format("/v2/%s/blobs/uploads/%s", name, uuid);
        String rangeHeader = status.getCurrentSize() > 0
                ? RangeUtils.buildRangeResponse(0, status.getCurrentSize() - 1, status.getCurrentSize())
                : "0-0";

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Location", uploadUrl)
                .header("Range", rangeHeader)
                .header("Docker-Upload-UUID", uuid)
                .build();
    }

    /**
     * DELETE /v2/{name}/blobs/uploads/{uuid} - 取消上传
     * 取消上传会话并清理临时文件
     * 
     * @param name 仓库名
     * @param uuid 上传会话UUID
     * @return 204 No Content（成功）或 404 Not Found（会话不存在）
     */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> cancelUpload(
            @PathVariable String name,
            @PathVariable String uuid) {

        log.debug("Cancel upload request: repository={}, uuid={}", name, uuid);

        // 验证会话存在并获取信息
        try {
            UploadSession session = uploadService.getUploadSession(uuid);

            // 验证仓库名匹配
            if (!name.equals(session.getRepository())) {
                return ResponseEntity.badRequest().build();
            }

            // 取消上传会话
            boolean cancelled = uploadService.cancelUploadSession(uuid);

            if (cancelled) {
                log.info("Upload session cancelled: repository={}, uuid={}", name, uuid);
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

        } catch (Exception e) {
            log.warn("Failed to cancel upload session: uuid={}", uuid, e);
            return ResponseEntity.notFound().build();
        }
    }
}

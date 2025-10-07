package com.diy.controller;

import com.diy.entity.Blob;
import com.diy.service.BlobService;
import com.diy.utils.RangeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;

/**
 * Blob相关API控制器
 * 处理Docker镜像层数据的下载和检查
 * 
 * @author diy
 */
@Slf4j
@RestController
@RequestMapping("/v2/{name}/blobs")
public class BlobController {

    @Autowired
    private BlobService blobService;

    /**
     * GET /v2/{name}/blobs/{digest} - 下载blob
     * 支持Range请求的分片下载
     * 
     * @param name   仓库名
     * @param digest blob的SHA256值
     * @param range  Range请求头（可选）
     * @return blob数据流
     */
    @GetMapping("/{digest}")
    public ResponseEntity<StreamingResponseBody> getBlob(
            @PathVariable String name,
            @PathVariable String digest,
            @RequestHeader(value = "Range", required = false) String range) {

        log.debug("Get blob request: repository={}, digest={}, range={}", name, digest, range);

        // 获取blob信息
        Blob blob = blobService.getBlobByDigest(digest);

        // 处理Range请求
        RangeUtils.RangeInfo rangeInfo = null;
        if (range != null) {
            rangeInfo = RangeUtils.parseRange(range, blob.getSize());
            if (!rangeInfo.isValid()) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header("Content-Range", "bytes */" + blob.getSize())
                        .build();
            }
        }

        // 创建流式响应
        final RangeUtils.RangeInfo finalRangeInfo = rangeInfo;
        StreamingResponseBody responseBody = outputStream -> {
            try (InputStream inputStream = blobService.getBlobInputStream(digest)) {

                byte[] buffer = new byte[8192];
                long totalBytesRead = 0;
                long startPosition = finalRangeInfo != null ? finalRangeInfo.getStart() : 0;
                long endPosition = finalRangeInfo != null ? finalRangeInfo.getEnd() : blob.getSize() - 1;
                long bytesToRead = endPosition - startPosition + 1;

                // 跳过开始位置之前的数据
                if (startPosition > 0) {
                    long skipped = 0;
                    while (skipped < startPosition) {
                        long toSkip = startPosition - skipped;
                        long actualSkipped = inputStream.skip(toSkip);
                        if (actualSkipped <= 0) {
                            // skip返回0时，用read方式跳过
                            byte[] skipBuffer = new byte[(int) Math.min(8192, toSkip)];
                            int readBytes = inputStream.read(skipBuffer);
                            if (readBytes <= 0)
                                break;
                            skipped += readBytes;
                        } else {
                            skipped += actualSkipped;
                        }
                    }
                    log.debug("Skipped {} bytes for range request", skipped);
                }

                // 读取并写入指定范围的数据
                long remainingBytes = bytesToRead;
                int bytesRead;
                while (remainingBytes > 0 && (bytesRead = inputStream.read(buffer, 0,
                        (int) Math.min(buffer.length, remainingBytes))) != -1) {

                    outputStream.write(buffer, 0, bytesRead);
                    remainingBytes -= bytesRead;
                    totalBytesRead += bytesRead;
                }

                outputStream.flush();
                log.debug("Blob download completed: digest={}, bytes_sent={}", digest, totalBytesRead);

            } catch (IOException e) {
                log.error("Failed to stream blob: digest={}", digest, e);
                try {
                    outputStream.flush();
                } catch (IOException ignored) {
                }
                throw new RuntimeException("Blob streaming failed", e);
            } catch (Exception e) {
                log.error("Unexpected error during blob streaming: digest={}", digest, e);
                try {
                    outputStream.flush();
                } catch (IOException ignored) {
                }
                throw new RuntimeException("Blob streaming failed", e);
            }
        };

        // 构建响应
        ResponseEntity.BodyBuilder responseBuilder;

        if (rangeInfo != null) {
            // 部分内容响应
            responseBuilder = ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .header("Content-Range", RangeUtils.buildContentRange(
                            rangeInfo.getStart(), rangeInfo.getEnd(), blob.getSize()))
                    .header("Content-Length", String.valueOf(rangeInfo.getLength()));
        } else {
            // 完整内容响应
            responseBuilder = ResponseEntity.ok()
                    .header("Content-Length", String.valueOf(blob.getSize()));
        }

        return responseBuilder
                .header("Content-Type", blob.getContentType())
                .header("Docker-Content-Digest", digest)
                .header("Accept-Ranges", "bytes")
                .body(responseBody);
    }

    /**
     * HEAD /v2/{name}/blobs/{digest} - 检查blob是否存在
     * 返回blob的元数据信息，不返回内容
     * 
     * @param name   仓库名
     * @param digest blob的SHA256值
     * @return 200 OK（存在）或 404 Not Found（不存在）
     */
    @RequestMapping(value = "/{digest}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headBlob(
            @PathVariable String name,
            @PathVariable String digest) {

        log.debug("Head blob request: repository={}, digest={}", name, digest);

        // 检查blob是否存在
        if (blobService.existsByDigest(digest)) {
            Blob blob = blobService.getBlobByDigest(digest);

            return ResponseEntity.ok()
                    .header("Content-Type", blob.getContentType())
                    .header("Content-Length", String.valueOf(blob.getSize()))
                    .header("Docker-Content-Digest", digest)
                    .header("Accept-Ranges", "bytes")
                    .build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}

package com.diy.exception;

import com.diy.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 全局异常处理器
 * 符合Docker Registry API错误响应规范
 * 
 * @author diy
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Blob未找到异常
     */
    @ExceptionHandler(BlobNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBlobNotFound(BlobNotFoundException e, HttpServletRequest request) {
        log.warn("Blob not found: {}, path: {}", e.getDigest(), request.getRequestURI());

        ErrorResponse error = new ErrorResponse(
                "BLOB_UNKNOWN",
                "blob unknown to registry",
                e.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(error);
    }

    /**
     * Manifest未找到异常
     */
    @ExceptionHandler(ManifestNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleManifestNotFound(ManifestNotFoundException e,
            HttpServletRequest request) {
        log.warn("Manifest not found: {}:{}, path: {}", e.getRepository(), e.getReference(), request.getRequestURI());

        ErrorResponse error = new ErrorResponse(
                "MANIFEST_UNKNOWN",
                "manifest unknown",
                e.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(error);
    }

    /**
     * 上传会话未找到异常
     */
    @ExceptionHandler(UploadSessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUploadSessionNotFound(UploadSessionNotFoundException e,
            HttpServletRequest request) {
        log.warn("Upload session not found: {}, path: {}", e.getUuid(), request.getRequestURI());

        ErrorResponse error = new ErrorResponse(
                "BLOB_UPLOAD_UNKNOWN",
                "blob upload unknown to registry",
                e.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(error);
    }

    /**
     * 无效摘要异常
     */
    @ExceptionHandler(InvalidDigestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDigest(InvalidDigestException e, HttpServletRequest request) {
        log.warn("Invalid digest: {}, path: {}", e.getDigest(), request.getRequestURI());

        ErrorResponse error = new ErrorResponse(
                "DIGEST_INVALID",
                "provided digest did not match uploaded content",
                e.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(error);
    }

    /**
     * 请求参数无效异常
     */
    @ExceptionHandler({ IllegalArgumentException.class, MethodArgumentTypeMismatchException.class })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e, HttpServletRequest request) {
        log.warn("Bad request: {}, path: {}", e.getMessage(), request.getRequestURI());

        ErrorResponse error = new ErrorResponse(
                "INVALID_REQUEST",
                "invalid request format",
                e.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(error);
    }

    /**
     * JSON解析异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleJsonParseError(HttpMessageNotReadableException e,
            HttpServletRequest request) {
        log.warn("JSON parse error: {}, path: {}", e.getMessage(), request.getRequestURI());

        ErrorResponse error = new ErrorResponse(
                "MANIFEST_INVALID",
                "manifest invalid",
                "Invalid JSON format");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(error);
    }

    /**
     * 参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(MethodArgumentNotValidException e,
            HttpServletRequest request) {
        log.warn("Validation error: {}, path: {}", e.getMessage(), request.getRequestURI());

        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        ErrorResponse error = new ErrorResponse(
                "INVALID_REQUEST",
                "request validation failed",
                detail);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(error);
    }

    /**
     * 方法不支持异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e,
            HttpServletRequest request) {
        log.warn("Method not supported: {} for path: {}", e.getMethod(), request.getRequestURI());

        ErrorResponse error = new ErrorResponse(
                "UNSUPPORTED",
                "The operation is unsupported",
                "Method " + e.getMethod() + " is not supported for this endpoint");

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header("Content-Type", "application/json")
                .body(error);
    }

    /**
     * 404异常
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoHandlerFoundException e, HttpServletRequest request) {
        log.warn("No handler found for: {} {}", e.getHttpMethod(), e.getRequestURL());

        ErrorResponse error = new ErrorResponse(
                "UNSUPPORTED",
                "The operation is unsupported",
                "Endpoint not found");

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(error);
    }

    /**
     * 通用异常处理
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericError(Exception e, HttpServletRequest request) {
        log.error("Unexpected error occurred, path: {}", request.getRequestURI(), e);

        ErrorResponse error = new ErrorResponse(
                "UNKNOWN",
                "unknown error",
                "An unexpected error occurred");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body(error);
    }
}

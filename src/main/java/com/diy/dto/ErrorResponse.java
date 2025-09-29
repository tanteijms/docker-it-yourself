package com.diy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 标准错误响应格式
 * 符合Docker Registry API规范
 * 
 * @author registry
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponse {

    /**
     * 错误信息列表
     */
    private List<ErrorDetail> errors;

    /**
     * 单个错误构造方法
     */
    public ErrorResponse(String code, String message, String detail) {
        this.errors = List.of(new ErrorDetail(code, message, detail));
    }

    /**
     * 错误详情
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ErrorDetail {
        /**
         * 错误代码
         * 例如：BLOB_UNKNOWN、MANIFEST_UNKNOWN、INVALID_REQUEST
         */
        private String code;

        /**
         * 错误消息
         */
        private String message;

        /**
         * 错误详情
         */
        private String detail;
    }
}

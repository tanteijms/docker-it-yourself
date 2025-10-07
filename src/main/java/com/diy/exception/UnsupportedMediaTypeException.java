package com.diy.exception;

/**
 * 不支持的媒体类型异常
 * 当上传的内容类型不符合要求时抛出
 * 
 * @author diy
 */
public class UnsupportedMediaTypeException extends RuntimeException {

    private final String contentType;

    public UnsupportedMediaTypeException(String contentType) {
        super("Unsupported media type: " + contentType);
        this.contentType = contentType;
    }

    public UnsupportedMediaTypeException(String contentType, String message) {
        super(message);
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }
}

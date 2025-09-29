package com.diy.exception;

/**
 * 上传会话未找到异常
 * 
 * @author diy
 */
public class UploadSessionNotFoundException extends RuntimeException {

    private final String uuid;

    public UploadSessionNotFoundException(String uuid) {
        super("Upload session not found: " + uuid);
        this.uuid = uuid;
    }

    public UploadSessionNotFoundException(String uuid, String message) {
        super(message);
        this.uuid = uuid;
    }

    public UploadSessionNotFoundException(String uuid, String message, Throwable cause) {
        super(message, cause);
        this.uuid = uuid;
    }

    public String getUuid() {
        return uuid;
    }
}

package com.diy.exception;

/**
 * Blob未找到异常
 * 
 * @author diy
 */
public class BlobNotFoundException extends RuntimeException {

    private final String digest;

    public BlobNotFoundException(String digest) {
        super("Blob not found: " + digest);
        this.digest = digest;
    }

    public BlobNotFoundException(String digest, String message) {
        super(message);
        this.digest = digest;
    }

    public BlobNotFoundException(String digest, String message, Throwable cause) {
        super(message, cause);
        this.digest = digest;
    }

    public String getDigest() {
        return digest;
    }
}

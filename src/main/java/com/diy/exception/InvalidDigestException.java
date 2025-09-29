package com.diy.exception;

/**
 * 无效摘要异常
 * 
 * @author diy
 */
public class InvalidDigestException extends RuntimeException {

    private final String digest;

    public InvalidDigestException(String digest) {
        super("Invalid digest format: " + digest);
        this.digest = digest;
    }

    public InvalidDigestException(String digest, String message) {
        super(message);
        this.digest = digest;
    }

    public InvalidDigestException(String digest, String message, Throwable cause) {
        super(message, cause);
        this.digest = digest;
    }

    public String getDigest() {
        return digest;
    }
}

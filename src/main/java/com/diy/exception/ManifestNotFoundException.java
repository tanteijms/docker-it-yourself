package com.diy.exception;

/**
 * Manifest未找到异常
 * 
 * @author diy
 */
public class ManifestNotFoundException extends RuntimeException {

    private final String repository;
    private final String reference;

    public ManifestNotFoundException(String repository, String reference) {
        super(String.format("Manifest not found: %s:%s", repository, reference));
        this.repository = repository;
        this.reference = reference;
    }

    public ManifestNotFoundException(String repository, String reference, String message) {
        super(message);
        this.repository = repository;
        this.reference = reference;
    }

    public ManifestNotFoundException(String repository, String reference, String message, Throwable cause) {
        super(message, cause);
        this.repository = repository;
        this.reference = reference;
    }

    public String getRepository() {
        return repository;
    }

    public String getReference() {
        return reference;
    }
}

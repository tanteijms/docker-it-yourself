package com.diy.utils;

// 不导入，直接使用完全限定名避免冲突

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 * SHA256摘要计算工具类
 * 
 * @author registry
 */
public class DigestUtils {

    /**
     * SHA256前缀
     */
    public static final String SHA256_PREFIX = "sha256:";

    /**
     * SHA256 digest格式验证正则
     * 格式：sha256: + 64位16进制字符
     */
    private static final Pattern DIGEST_PATTERN = Pattern.compile("^sha256:[a-f0-9]{64}$");

    /**
     * 计算字符串的SHA256值
     * 
     * @param content 字符串内容
     * @return SHA256值，格式：sha256:abc123...
     */
    public static String calculateSHA256(String content) {
        return SHA256_PREFIX
                + org.apache.commons.codec.digest.DigestUtils.sha256Hex(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组的SHA256值
     * 
     * @param bytes 字节数组
     * @return SHA256值，格式：sha256:abc123...
     */
    public static String calculateSHA256(byte[] bytes) {
        return SHA256_PREFIX + org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);
    }

    /**
     * 计算输入流的SHA256值
     * 
     * @param inputStream 输入流
     * @return SHA256值，格式：sha256:abc123...
     * @throws IOException IO异常
     */
    public static String calculateSHA256(InputStream inputStream) throws IOException {
        return SHA256_PREFIX + org.apache.commons.codec.digest.DigestUtils.sha256Hex(inputStream);
    }

    /**
     * 创建SHA256消息摘要实例
     * 
     * @return MessageDigest实例
     */
    public static MessageDigest createSHA256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * 验证digest格式是否正确
     * 
     * @param digest 要验证的digest
     * @return 是否有效
     */
    public static boolean isValidDigest(String digest) {
        return digest != null && DIGEST_PATTERN.matcher(digest).matches();
    }

    /**
     * 提取digest中的哈希值部分（去除sha256:前缀）
     * 
     * @param digest 完整的digest
     * @return 哈希值部分
     */
    public static String extractHash(String digest) {
        if (!isValidDigest(digest)) {
            throw new IllegalArgumentException("Invalid digest format: " + digest);
        }
        return digest.substring(SHA256_PREFIX.length());
    }

    /**
     * 从哈希值构造完整的digest
     * 
     * @param hash 64位16进制哈希值
     * @return 完整的digest
     */
    public static String buildDigest(String hash) {
        if (hash == null || hash.length() != 64 || !hash.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Invalid hash format: " + hash);
        }
        return SHA256_PREFIX + hash;
    }
}

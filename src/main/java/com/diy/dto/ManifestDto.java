package com.diy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Manifest数据传输对象
 * 用于解析和处理Docker Manifest JSON
 * 
 * @author registry
 */
@Data
public class ManifestDto {

    /**
     * 模式版本
     */
    @JsonProperty("schemaVersion")
    private Integer schemaVersion;

    /**
     * 媒体类型
     */
    @JsonProperty("mediaType")
    private String mediaType;

    /**
     * 配置信息
     */
    @JsonProperty("config")
    private ConfigDto config;

    /**
     * 层列表
     */
    @JsonProperty("layers")
    private List<LayerDto> layers;

    /**
     * Manifest列表（用于manifest list）
     */
    @JsonProperty("manifests")
    private List<ManifestReferenceDto> manifests;

    /**
     * 配置信息DTO
     */
    @Data
    public static class ConfigDto {
        @JsonProperty("mediaType")
        private String mediaType;

        @JsonProperty("digest")
        private String digest;

        @JsonProperty("size")
        private Long size;

        /**
         * 可选的内联数据（base64编码）
         */
        @JsonProperty("data")
        private String data;
    }

    /**
     * 层信息DTO
     */
    @Data
    public static class LayerDto {
        @JsonProperty("mediaType")
        private String mediaType;

        @JsonProperty("digest")
        private String digest;

        @JsonProperty("size")
        private Long size;
    }

    /**
     * Manifest引用DTO（用于manifest list）
     */
    @Data
    public static class ManifestReferenceDto {
        @JsonProperty("mediaType")
        private String mediaType;

        @JsonProperty("digest")
        private String digest;

        @JsonProperty("size")
        private Long size;

        @JsonProperty("platform")
        private PlatformDto platform;
    }

    /**
     * 平台信息DTO
     */
    @Data
    public static class PlatformDto {
        @JsonProperty("architecture")
        private String architecture;

        @JsonProperty("os")
        private String os;
    }
}

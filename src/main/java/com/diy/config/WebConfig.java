package com.diy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置
 * 
 * @author diy
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Docker Registry支持的媒体类型
     */
    public static final String MANIFEST_V2_MEDIA_TYPE = "application/vnd.docker.distribution.manifest.v2+json";
    public static final String MANIFEST_LIST_V2_MEDIA_TYPE = "application/vnd.docker.distribution.manifest.list.v2+json";
    public static final String OCI_MANIFEST_MEDIA_TYPE = "application/vnd.oci.image.manifest.v1+json";
    public static final String OCI_INDEX_MEDIA_TYPE = "application/vnd.oci.image.index.v1+json";

    /**
     * 配置内容协商
     */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
                .favorParameter(false)
                .favorPathExtension(false)
                .ignoreAcceptHeader(false)
                .defaultContentType(MediaType.APPLICATION_JSON)
                // 添加Docker Registry特定的媒体类型
                .mediaType("manifest", MediaType.parseMediaType(MANIFEST_V2_MEDIA_TYPE))
                .mediaType("manifestlist", MediaType.parseMediaType(MANIFEST_LIST_V2_MEDIA_TYPE))
                .mediaType("oci-manifest", MediaType.parseMediaType(OCI_MANIFEST_MEDIA_TYPE))
                .mediaType("oci-index", MediaType.parseMediaType(OCI_INDEX_MEDIA_TYPE));
    }

    /**
     * 配置CORS（如果需要）
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/v2/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "HEAD", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

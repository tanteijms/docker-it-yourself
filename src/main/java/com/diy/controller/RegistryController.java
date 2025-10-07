package com.diy.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Docker Registry API根路径控制器
 * 处理基础的Registry API端点
 * 
 * @author diy
 */
@Slf4j
@RestController
@RequestMapping("/v2")
public class RegistryController {

    /**
     * GET /v2/ - Registry API版本检查
     * 这是Docker客户端用来检测Registry是否支持v2 API的端点
     * 
     * @return 200 OK表示支持v2 API
     */
    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> checkApiVersion() {
        log.debug("Registry API version check requested");

        Map<String, Object> response = new HashMap<>();
        response.put("name", "Docker It Yourself Registry");
        response.put("version", "v2");
        response.put("description", "Docker Registry HTTP API V2 Implementation");

        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .header("Docker-Distribution-Api-Version", "registry/2.0")
                .body(response);
    }

    /**
     * GET /v2/_ping - Registry健康检查
     * 用于检查Registry服务是否正常运行
     * 
     * @return 200 OK表示服务正常
     */
    @GetMapping("/_ping")
    public ResponseEntity<Void> ping() {
        log.debug("Registry ping requested");

        return ResponseEntity.ok()
                .header("Docker-Distribution-Api-Version", "registry/2.0")
                .build();
    }
}

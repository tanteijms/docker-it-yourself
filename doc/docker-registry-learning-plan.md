# Docker Registry 学习计划

## 概述
本计划旨在帮助具有Java后端基础的开发者完成Docker Registry HTTP API V2规范的后端服务实现。

**预计总时间**: 25-36天  
**技术栈**: Java Spring Boot + PostgreSQL + 文件存储  
**最终目标**: 实现符合Docker Registry HTTP API V2规范的完整后端服务

---

## 阶段1：Docker基础理解 (3-5天)

### 学习目标
- 深入理解Docker的存储原理和镜像结构
- 掌握Docker镜像的层级概念和数据流向
- 理解Registry在Docker生态中的作用

### Day 1-2: Docker基础概念
**学习内容**：
- Docker镜像的层级结构（Layer）概念
- 理解 digest、tag、repository 的区别和关系
- 学习 `docker pull/push` 的底层工作原理
- 了解镜像存储的Copy-on-Write机制

**实践任务**：
```bash
# 分析镜像结构
docker history ubuntu:latest
docker inspect ubuntu:latest

# 查看镜像层信息
docker image inspect ubuntu:latest | jq '.[0].RootFS'
```

**产出**：
- [ ] 完成Docker基础概念笔记
- [ ] 实践分析至少3个不同镜像的结构

### Day 3-4: 镜像格式深入
**学习内容**：
- OCI (Open Container Initiative) 规范详解
- Manifest 和 Manifest List 的格式和用途
- 理解 SHA256 校验机制在容器生态中的作用
- 学习镜像的多架构支持原理

**实践任务**：
```bash
# 手动获取manifest
curl -H "Accept: application/vnd.docker.distribution.manifest.v2+json" \
     https://registry-1.docker.io/v2/library/ubuntu/manifests/latest

# 分析manifest内容
docker manifest inspect ubuntu:latest
```

**产出**：
- [ ] 完成OCI规范学习笔记
- [ ] 手动解析一个真实镜像的manifest并理解每个字段

### Day 5: 网络抓包分析
**学习内容**：

- 使用网络抓包工具分析Docker通信过程
- 理解HTTP请求的具体格式和时序
- 分析分片下载的实现细节

**实践任务**：
```bash
# 抓包分析docker pull过程
tcpdump -i any -s 0 -w docker-pull.pcap port 443 &
docker pull alpine:latest
```

**产出**：

- [ ] 完成Docker网络通信分析报告
- [ ] 理解完整的pull/push流程

---

## 阶段2：Registry原理深入 (3-4天)

### 学习目标
- 理解Docker Registry的架构设计
- 掌握Registry的存储模型和数据组织方式
- 学习主流Registry实现的设计思路

### Day 1-2: Registry架构研究
**学习内容**：
- Docker Hub、Harbor、Quay等主流Registry架构对比
- Registry的存储模型和数据持久化策略
- 高可用和负载均衡的实现方案

**实践任务**：
```bash
# 搭建本地Registry进行实验
docker run -d -p 5000:5000 --name registry registry:2

# 推送测试镜像
docker tag ubuntu:latest localhost:5000/ubuntu:test
docker push localhost:5000/ubuntu:test
```

**产出**：
- [ ] 完成主流Registry架构对比分析
- [ ] 搭建并测试本地Registry

### Day 3-4: 数据流和状态管理
**学习内容**：
- 分析 `docker push` 的完整数据流
- 理解分片上传的状态管理机制
- 学习错误处理和重试策略
- 研究并发上传的处理方案

**实践任务**：
- 阅读Docker Distribution项目源码（重点关注接口定义）
- 分析Registry的存储目录结构

**产出**：
- [ ] 完成Registry数据流分析文档
- [ ] 理解分片上传的状态机设计

---

## 阶段3：API文档研读和设计 (4-5天)

### 学习目标
- 完全理解Docker Registry HTTP API V2规范
- 设计符合规范的REST接口
- 完成数据模型和数据库设计

### Day 1-2: API规范精读
**学习内容**：
- 逐个分析11个API端点的详细规范
- 理解每个HTTP状态码的准确含义
- 掌握Content-Type处理的具体要求
- 学习错误响应的标准格式

**重点API端点**：
1. `GET /v2/{name}/manifests/{reference}` - 获取manifest或manifest list
2. `PUT /v2/{name}/manifests/{reference}` - 上传manifest
3. `GET /v2/{name}/blobs/{digest}` - 下载blob
4. `POST /v2/{name}/blobs/uploads/` - 开始blob上传
5. `PATCH /v2/{name}/blobs/uploads/{uuid}` - 分片上传blob
6. `PUT /v2/{name}/blobs/uploads/{uuid}` - 完成blob上传

**产出**：
- [ ] 完成API规范详细分析文档
- [ ] 整理每个端点的请求响应格式

### Day 3-4: 接口和数据模型设计
**学习内容**：
- 使用Spring Boot设计REST接口架构
- 定义核心数据模型和实体关系
- 设计数据库表结构和索引策略

**核心数据模型**：
```java
// Blob实体
@Entity
@Table(name = "blobs")
public class Blob {
    @Id
    private String digest;          // SHA256值，主键
    
    @Column(nullable = false)
    private Long size;              // 文件大小
    
    @Column(name = "content_type")
    private String contentType;     // MIME类型
    
    @Column(name = "file_path")
    private String filePath;        // 实际存储路径
    
    @Column(name = "created_at")
    private LocalDateTime createdAt; // 创建时间
}

// Manifest实体
@Entity
@Table(name = "manifests")
public class Manifest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String digest;          // SHA256值
    
    @Column(nullable = false)
    private String repository;      // 仓库名
    
    private String tag;             // 标签，可为空
    
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;         // JSON内容
    
    @Column(name = "media_type")
    private String mediaType;       // manifest类型
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

// 上传会话实体
@Entity
@Table(name = "upload_sessions")
public class UploadSession {
    @Id
    private String uuid;            // 上传会话ID
    
    @Column(nullable = false)
    private String repository;      // 仓库名
    
    @Column(name = "started_at")
    private LocalDateTime startedAt; // 开始时间
    
    @Column(name = "last_activity")
    private LocalDateTime lastActivity; // 最后活动时间
    
    private Long size;              // 已上传大小
    
    @Column(name = "temp_path")
    private String tempPath;        // 临时文件路径
    
    @Enumerated(EnumType.STRING)
    private UploadStatus status;    // 上传状态
}
```

**产出**：
- [ ] 完成Spring Boot项目架构设计
- [ ] 完成数据库设计文档和DDL脚本

### Day 5: Mock实现
**学习内容**：
- 创建接口的Mock版本进行快速验证
- 搭建项目基础架构和开发环境
- 配置基础的请求响应处理

**项目结构**：
```
docker-registry/
├── src/main/java/com/registry/
│   ├── RegistryApplication.java
│   ├── controller/
│   │   ├── ManifestController.java
│   │   ├── BlobController.java
│   │   └── UploadController.java
│   ├── service/
│   │   ├── ManifestService.java
│   │   ├── BlobService.java
│   │   └── UploadService.java
│   ├── repository/
│   │   ├── BlobRepository.java
│   │   ├── ManifestRepository.java
│   │   └── UploadSessionRepository.java
│   ├── entity/
│   └── config/
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
└── pom.xml
```

**产出**：
- [ ] 完成项目基础架构搭建
- [ ] 实现基础的Mock接口

---

## 阶段4：基础功能实现 (5-7天)

### 学习目标
- 实现核心的blob存储和检索功能
- 完成基础的manifest操作
- 建立完整的数据校验机制

### Day 1-2: 项目搭建和配置
**技术栈选择**：
```xml
<!-- 推荐依赖 -->
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
    <dependency>
        <groupId>commons-codec</groupId>
        <artifactId>commons-codec</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
</dependencies>
```

**环境配置**：
```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/docker_registry
    username: registry
    password: registry
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    
registry:
  storage:
    root-path: /var/lib/registry
    blob-path: ${registry.storage.root-path}/blobs
    temp-path: ${registry.storage.root-path}/temp
```

**产出**：
- [ ] 完成开发环境搭建
- [ ] 配置数据库和基础服务

### Day 3-4: Blob存储实现
**核心功能**：
1. **Blob下载** (`GET /v2/{name}/blobs/{digest}`)
```java
@GetMapping("/v2/{name}/blobs/{digest}")
public ResponseEntity<Resource> downloadBlob(
    @PathVariable String name,
    @PathVariable String digest,
    HttpServletRequest request) {
    
    // 验证digest格式
    if (!isValidDigest(digest)) {
        return ResponseEntity.badRequest().build();
    }
    
    // 检查blob是否存在
    Optional<Blob> blob = blobRepository.findByDigest(digest);
    if (blob.isEmpty()) {
        return ResponseEntity.notFound().build();
    }
    
    // 返回文件内容
    Resource resource = storageService.loadBlobAsResource(blob.get());
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, blob.get().getContentType())
        .header("Docker-Content-Digest", digest)
        .body(resource);
}
```

2. **Blob存在性检查** (`HEAD /v2/{name}/blobs/{digest}`)
3. **SHA256校验机制**

**存储策略**：
```
/var/lib/registry/blobs/
  └── sha256/
      └── ab/
          └── abc123def456.../
              └── data
```

**产出**：
- [ ] 完成blob存储和检索功能
- [ ] 实现SHA256校验机制

### Day 5-7: Manifest基础操作
**核心功能**：
1. **Manifest获取** (`GET /v2/{name}/manifests/{reference}`)
```java
@GetMapping("/v2/{name}/manifests/{reference}")
public ResponseEntity<String> getManifest(
    @PathVariable String name,
    @PathVariable String reference,
    @RequestHeader(value = "Accept", required = false) String accept) {
    
    // 判断reference类型
    if (isDigest(reference)) {
        // 返回具体的manifest
        return getManifestByDigest(name, reference, accept);
    } else {
        // 返回manifest list
        return getManifestListByTag(name, reference, accept);
    }
}
```

2. **Content-Type处理**
3. **JSON解析和验证**

**产出**：
- [ ] 完成manifest基础操作
- [ ] 实现Content-Type正确处理

---

## 阶段5：高级功能实现 (7-10天)

### 学习目标
- 实现完整的分片上传系统
- 完成manifest上传和依赖检查
- 建立数据一致性保障机制

### Day 1-3: 分片上传系统
**核心挑战**：
- 上传会话管理（UUID生成和状态跟踪）
- 分片数据临时存储和组装
- 上传完成后的数据合并和校验
- 异常情况处理（超时、中断、重试）

**关键实现**：
1. **开始上传** (`POST /v2/{name}/blobs/uploads/`)
```java
@PostMapping("/v2/{name}/blobs/uploads/")
public ResponseEntity<Void> startBlobUpload(@PathVariable String name) {
    String uuid = UUID.randomUUID().toString();
    
    UploadSession session = new UploadSession();
    session.setUuid(uuid);
    session.setRepository(name);
    session.setStartedAt(LocalDateTime.now());
    session.setStatus(UploadStatus.ACTIVE);
    
    uploadSessionRepository.save(session);
    
    String location = String.format("/v2/%s/blobs/uploads/%s", name, uuid);
    return ResponseEntity.accepted()
        .header("Location", location)
        .header("Range", "0-0")
        .build();
}
```

2. **分片上传** (`PATCH /v2/{name}/blobs/uploads/{uuid}`)
3. **完成上传** (`PUT /v2/{name}/blobs/uploads/{uuid}`)

**产出**：
- [ ] 完成分片上传完整实现
- [ ] 建立上传会话管理机制

### Day 4-6: Manifest上传逻辑
**核心功能**：
1. **依赖检查** - 确保所有引用的blob存在
2. **Manifest List自动生成和更新**
3. **事务处理确保数据一致性**

**Manifest上传实现**：
```java
@PutMapping("/v2/{name}/manifests/{reference}")
public ResponseEntity<Void> putManifest(
    @PathVariable String name,
    @PathVariable String reference,
    @RequestBody String manifestContent,
    @RequestHeader("Content-Type") String contentType) {
    
    // 解析并验证manifest
    ManifestData manifest = parseManifest(manifestContent, contentType);
    
    // 检查所有依赖的blob是否存在
    for (String digest : manifest.getBlobDigests()) {
        if (!blobRepository.existsByDigest(digest)) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .build();
        }
    }
    
    // 保存manifest并更新manifest list
    String manifestDigest = calculateDigest(manifestContent);
    manifestService.saveManifest(name, reference, manifestContent, 
                                contentType, manifestDigest);
    
    return ResponseEntity.created(
        URI.create(String.format("/v2/%s/manifests/%s", name, manifestDigest)))
        .header("Docker-Content-Digest", manifestDigest)
        .build();
}
```

**产出**：
- [ ] 完成manifest上传功能
- [ ] 实现依赖检查和数据一致性

### Day 7-10: 高级特性和优化
**功能完善**：
1. **Range请求支持** - 实现分片下载
2. **并发控制** - 处理多用户同时操作
3. **垃圾回收** - 清理无用的blob和过期会话
4. **错误处理** - 完善各种异常情况的处理
5. **性能优化** - 添加适当的缓存和索引

**Range请求实现**：
```java
@GetMapping("/v2/{name}/blobs/{digest}")
public ResponseEntity<Resource> downloadBlobWithRange(
    @PathVariable String name,
    @PathVariable String digest,
    @RequestHeader(value = "Range", required = false) String range) {
    
    if (range != null) {
        // 解析Range header并返回部分内容
        return handleRangeRequest(digest, range);
    }
    
    // 正常返回完整内容
    return downloadBlob(name, digest);
}
```

**产出**：
- [ ] 完成所有高级特性
- [ ] 性能优化和稳定性提升

---

## 阶段6：测试和完善 (3-5天)

### 学习目标
- 确保系统稳定性和规范符合性
- 通过真实Docker客户端验证功能
- 完善文档和代码质量

### Day 1-2: 集成测试
**测试策略**：
1. **Docker客户端测试**
```bash
# 配置本地registry
echo '{"insecure-registries":["localhost:8080"]}' > /etc/docker/daemon.json
systemctl restart docker

# 完整推送拉取测试
docker tag ubuntu:latest localhost:8080/test/ubuntu:v1
docker push localhost:8080/test/ubuntu:v1
docker rmi localhost:8080/test/ubuntu:v1
docker pull localhost:8080/test/ubuntu:v1
```

2. **自动化测试脚本**
3. **压力测试和并发测试**

**产出**：
- [ ] 通过Docker客户端完整测试
- [ ] 编写自动化测试套件

### Day 3-5: 规范符合性和完善
**检查项目**：
1. **API规范符合性** - 对照官方文档检查每个细节
2. **错误响应格式** - 确保符合标准格式
3. **HTTP状态码** - 验证所有状态码使用正确
4. **Content-Type处理** - 确保支持所有要求的类型

**代码质量提升**：
- 添加详细注释和文档
- 代码重构和优化
- 添加日志和监控
- 安全性检查

**产出**：
- [ ] 完成规范符合性验证
- [ ] 代码质量达到生产标准

---

## 关键技术要点总结

### 1. 文件存储策略
```java
public class BlobStorageService {
    private static final String BLOB_PATH_PATTERN = "blobs/sha256/%s/%s/data";
    
    public String generateBlobPath(String digest) {
        String prefix = digest.substring(7, 9); // 取sha256:后的前2位
        return String.format(BLOB_PATH_PATTERN, prefix, digest.substring(7));
    }
}
```

### 2. 数据校验机制
```java
public class DigestUtils {
    public static String calculateSHA256(InputStream input) throws IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return "sha256:" + Hex.encodeHexString(digest.digest());
    }
}
```

### 3. 错误处理标准
```java
@RestControllerAdvice
public class RegistryExceptionHandler {
    @ExceptionHandler(BlobNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBlobNotFound(BlobNotFoundException e) {
        ErrorResponse error = new ErrorResponse();
        error.setCode("BLOB_UNKNOWN");
        error.setMessage("blob unknown to registry");
        error.setDetail(e.getMessage());
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .header("Content-Type", "application/json")
            .body(error);
    }
}
```

---

## 学习资源

### 官方文档
- [Docker Registry HTTP API V2](https://docs.docker.com/reference/api/registry/latest/)
- [OCI Distribution Specification](https://github.com/opencontainers/distribution-spec)

### 参考实现
- [Docker Distribution](https://github.com/distribution/distribution) (Go)
- [Harbor](https://github.com/goharbor/harbor) (Go)

### 开发工具
- **IDE**: IntelliJ IDEA / Eclipse
- **API测试**: Postman / curl
- **数据库**: PostgreSQL + pgAdmin
- **容器**: Docker Desktop

### 调试工具
- **网络分析**: Wireshark / tcpdump
- **HTTP调试**: Charles Proxy / Fiddler
- **日志分析**: ELK Stack (可选)

---

## 时间安排建议

### 全职学习 (推荐)
- **总时间**: 25-36天
- **每日安排**: 6-8小时学习 + 2-4小时实践
- **重点时间分配**: 阶段4和阶段5各占40%的时间

### 业余时间学习
- **总时间**: 50-70天
- **每日安排**: 2-3小时学习实践
- **周末集中**: 4-6小时深度开发

### 学习进度检查点
- **第1周末**: 完成阶段1-2，理解Docker和Registry原理
- **第2周末**: 完成阶段3，完成接口设计
- **第3-4周**: 完成阶段4-5，实现核心功能
- **第5周**: 完成阶段6，测试和完善

---

## 成功标准

### 最低要求 (通过基础测试)
- [ ] 实现所有11个API端点
- [ ] 支持Docker客户端的push/pull操作
- [ ] 正确处理blob分片上传
- [ ] 实现数据完整性校验
- [ ] 基本的错误处理

### 理想目标 (生产级别)
- [ ] 性能优化和并发控制
- [ ] 完善的错误处理和日志
- [ ] 自动化测试覆盖
- [ ] 代码质量和文档完善
- [ ] 支持高可用部署

---

## 风险提示

### 常见难点
1. **分片上传状态管理** - 需要仔细设计状态机
2. **manifest依赖检查** - 确保数据一致性
3. **并发处理** - 避免竞态条件
4. **错误处理** - 符合规范的错误响应

### 调试技巧
1. **使用真实Docker客户端测试** - 最直接的验证方式
2. **抓包分析** - 对比标准Registry的行为
3. **单元测试驱动** - 确保每个组件正确工作
4. **逐步增加复杂度** - 先实现简单功能再添加高级特性

### 时间管理
1. **不要过度设计** - 先实现基本功能
2. **及时测试验证** - 避免累积错误
3. **记录学习过程** - 便于回顾和调试
4. **寻求帮助** - 遇到困难及时沟通

---

祝你学习顺利！这是一个很有挑战性但非常有价值的项目，完成后你将对容器生态有深入的理解。

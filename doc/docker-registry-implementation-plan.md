# Docker Registry 实现方案

## 项目概述

**目标**：实现符合 Docker Registry HTTP API V2 规范的简化后端服务，通过出题人的集成测试  
**技术栈**：Java Spring Boot + MySQL + 阿里云OSS + MyBatis  
**预计时间**：2-3周（基于有Java Spring Boot经验）  
**关键成功指标**：通过集成测试

---

## 核心技术要求分析

### 必须实现的功能
1. **11个API端点**（4个路径）
   - `/v2/{name}/manifests/{reference}` - Manifest操作 
   - `/v2/{name}/blobs/{digest}` - Blob下载和检查
   - `/v2/{name}/blobs/uploads` - 开始上传
   - `/v2/{name}/blobs/uploads/{uuid}` - 分片上传和完成

2. **核心技术指标**
   - ✅ Blob分片上传功能（最复杂的部分）
   - ✅ 完整性校验（SHA256验证）
   - ✅ 错误处理（至少400和404响应）
   - ✅ Content-Type正确处理（两种Manifest类型）
   - ✅ Linux环境运行

3. **数据实体管理**
   - Blob：二进制数据块
   - Manifest：镜像元数据
   - Manifest List：Tag对应的Manifest列表

---

## 精简实现方案（14天）

### 第1-2天：项目搭建和核心架构
**目标**：建立项目基础架构，理解API规范

**技术栈选择**：
```xml
<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- MyBatis Starter -->
    <dependency>
        <groupId>org.mybatis.spring.boot</groupId>
        <artifactId>mybatis-spring-boot-starter</artifactId>
        <version>3.0.3</version>
    </dependency>
    
    <!-- MySQL驱动 -->
    <dependency>
        <groupId>mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
        <scope>runtime</scope>
    </dependency>
    
    <!-- 阿里云OSS SDK -->
    <dependency>
        <groupId>com.aliyun.oss</groupId>
        <artifactId>aliyun-sdk-oss</artifactId>
        <version>3.17.4</version>
    </dependency>
    
    <!-- 参数校验 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <!-- SHA256计算 -->
    <dependency>
        <groupId>commons-codec</groupId>
        <artifactId>commons-codec</artifactId>
    </dependency>
    
    <!-- JSON处理 -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    
    <!-- 测试 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    
    <!-- 测试用H2数据库 -->
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**项目结构**：
```
docker-registry/
├── src/main/java/com/registry/
│   ├── DockerRegistryApplication.java
│   ├── controller/
│   │   ├── ManifestController.java      # Manifest相关API
│   │   ├── BlobController.java          # Blob下载检查
│   │   └── UploadController.java        # Blob上传API
│   ├── service/
│   │   ├── ManifestService.java
│   │   ├── BlobService.java
│   │   ├── UploadService.java
│   │   └── OssStorageService.java       # OSS存储服务
│   ├── mapper/                          # MyBatis Mapper接口
│   │   ├── BlobMapper.java
│   │   ├── ManifestMapper.java
│   │   └── UploadSessionMapper.java
│   ├── entity/
│   │   ├── Blob.java
│   │   ├── Manifest.java
│   │   └── UploadSession.java
│   ├── dto/
│   │   ├── ManifestDto.java
│   │   └── ErrorResponse.java
│   ├── config/
│   │   ├── MyBatisConfig.java
│   │   └── OssConfig.java               # OSS配置
│   └── exception/
│       ├── BlobNotFoundException.java
│       └── GlobalExceptionHandler.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-test.yml
│   └── mapper/                          # MyBatis SQL映射文件
│       ├── BlobMapper.xml
│       ├── ManifestMapper.xml
│       └── UploadSessionMapper.xml
└── sql/                                 # 数据库脚本
    └── schema.sql
```

**核心配置**：

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/docker_registry?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: registry_user
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000

# MyBatis配置
mybatis:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl

# 阿里云OSS配置
aliyun:
  oss:
    endpoint: https://oss-cn-hangzhou.aliyuncs.com  # 您的OSS区域
    bucket-name: your-docker-registry-bucket        # 您的bucket名称
    access-key-id: ${OSS_ACCESS_KEY_ID}             # 环境变量
    access-key-secret: ${OSS_ACCESS_KEY_SECRET}     # 环境变量
    # 路径前缀配置
    blob-prefix: blobs/                             # blob存储路径前缀
    temp-prefix: temp/                              # 临时文件路径前缀

server:
  port: 8080

logging:
  level:
    com.registry: DEBUG
    com.registry.mapper: DEBUG
```

```yaml
# application-test.yml (测试环境)
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password: 
    
mybatis:
  configuration:
    map-underscore-to-camel-case: true
    
# 测试环境不使用OSS
aliyun:
  oss:
    enabled: false
```

**MySQL数据库初始化**：
```sql
-- schema.sql
CREATE DATABASE IF NOT EXISTS docker_registry DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE docker_registry;

CREATE TABLE blobs (
    digest VARCHAR(71) PRIMARY KEY COMMENT 'SHA256 digest，格式：sha256:xxx',
    size BIGINT NOT NULL COMMENT '文件大小（字节）',
    oss_object_key VARCHAR(500) NOT NULL COMMENT 'OSS对象存储key',
    content_type VARCHAR(100) DEFAULT 'application/octet-stream' COMMENT 'MIME类型',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Blob存储表';

CREATE TABLE manifests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    digest VARCHAR(71) NOT NULL UNIQUE COMMENT 'Manifest的SHA256值',
    repository VARCHAR(255) NOT NULL COMMENT '仓库名称',
    tag VARCHAR(128) COMMENT '标签（可为空，通过digest访问时为空）',
    content TEXT NOT NULL COMMENT 'Manifest JSON内容',
    media_type VARCHAR(100) NOT NULL COMMENT 'Content-Type',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_repo_tag (repository, tag),
    INDEX idx_repo_digest (repository, digest)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Manifest存储表';

CREATE TABLE upload_sessions (
    uuid VARCHAR(36) PRIMARY KEY COMMENT '上传会话UUID',
    repository VARCHAR(255) NOT NULL COMMENT '仓库名称',
    oss_temp_key VARCHAR(500) COMMENT 'OSS临时文件key',
    current_size BIGINT DEFAULT 0 COMMENT '已上传字节数',
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    last_activity TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活动时间',
    status ENUM('ACTIVE', 'COMPLETED', 'EXPIRED') DEFAULT 'ACTIVE' COMMENT '状态',
    INDEX idx_status (status),
    INDEX idx_last_activity (last_activity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上传会话表';
```

**MySQL环境准备**：
```bash
# 创建数据库和用户
mysql -u root -p
CREATE DATABASE docker_registry DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'registry_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON docker_registry.* TO 'registry_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

**OSS存储服务配置**：
```java
@Configuration
@ConfigurationProperties(prefix = "aliyun.oss")
@Data
public class OssProperties {
    private String endpoint;
    private String bucketName;
    private String accessKeyId;
    private String accessKeySecret;
    private String blobPrefix = "blobs/";
    private String tempPrefix = "temp/";
}

@Configuration
public class OssConfig {
    
    @Bean
    public OSS ossClient(OssProperties ossProperties) {
        return new OSSClientBuilder().build(
            ossProperties.getEndpoint(),
            ossProperties.getAccessKeyId(),
            ossProperties.getAccessKeySecret()
        );
    }
}
```

**产出**：
- [ ] 完成MySQL环境搭建
- [ ] 完成OSS配置和连接测试
- [ ] 完成项目基础架构
- [ ] 建立数据模型和存储策略

---

## 开发测试环境配置

### 🖥️ **Windows开发 + WSL2测试方案**

**环境策略**：Windows进行日常开发调试，WSL2进行Linux环境集成测试，满足出题人要求。

### 开发环境（Windows）

**必需软件**：
- **IDE**: IntelliJ IDEA Community/Ultimate
- **JDK**: OpenJDK 17+ (推荐Eclipse Temurin)
- **Maven**: 3.8+
- **MySQL**: 8.0+ (Windows版本或Docker Desktop)
- **Git**: Windows版本

**开发配置** (`application-dev.yml`):
```yaml
# Windows开发环境配置
spring:
  profiles:
    active: dev
  datasource:
    url: jdbc:mysql://localhost:3306/docker_registry?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: root
    password: your_win_password
    driver-class-name: com.mysql.cj.jdbc.Driver

# MyBatis配置
mybatis:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl

# OSS配置（开发测试用）
aliyun:
  oss:
    endpoint: https://oss-cn-hangzhou.aliyuncs.com
    bucket-name: your-docker-registry-bucket
    access-key-id: ${OSS_ACCESS_KEY_ID}
    access-key-secret: ${OSS_ACCESS_KEY_SECRET}
    blob-prefix: dev-blobs/
    temp-prefix: dev-temp/

server:
  port: 8080

logging:
  level:
    com.registry: DEBUG
    com.registry.mapper: DEBUG
```

**Windows开发流程**：
```powershell
# 1. 克隆项目
git clone <project-url>
cd docker-registry

# 2. 设置环境变量
$env:OSS_ACCESS_KEY_ID="your_access_key"
$env:OSS_ACCESS_KEY_SECRET="your_secret_key"

# 3. 启动MySQL（如果用Docker）
docker run -d --name mysql-dev -p 3306:3306 -e MYSQL_ROOT_PASSWORD=your_password mysql:8.0

# 4. 运行项目
mvn clean package
java -jar target/docker-registry-*.jar --spring.profiles.active=dev
```

### 测试环境（WSL2 Ubuntu）

**WSL2环境准备**：
```bash
# 安装WSL2 Ubuntu
wsl --install -d Ubuntu

# 进入WSL2并安装必需软件
wsl
sudo apt update
sudo apt install -y openjdk-17-jdk maven mysql-client docker.io curl

# 启动Docker服务
sudo service docker start

# 添加用户到docker组
sudo usermod -aG docker $USER
# 重新登录使权限生效
exit
wsl
```

**Linux测试配置** (`application-linux.yml`):
```yaml
# Linux测试环境配置
spring:
  profiles:
    active: linux
  datasource:
    # 连接Windows的MySQL数据库
    url: jdbc:mysql://host.docker.internal:3306/docker_registry?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: root
    password: your_win_password
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true

# 使用相同的OSS配置
aliyun:
  oss:
    endpoint: https://oss-cn-hangzhou.aliyuncs.com
    bucket-name: your-docker-registry-bucket
    access-key-id: ${OSS_ACCESS_KEY_ID}
    access-key-secret: ${OSS_ACCESS_KEY_SECRET}
    blob-prefix: test-blobs/
    temp-prefix: test-temp/

server:
  port: 8080

logging:
  level:
    com.registry: INFO
```

**项目目录结构**：
```
项目目录规划：
Windows: C:\Users\YourName\Projects\docker-registry\
├── src/main/java/
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml      # Windows开发配置
│   └── application-linux.yml    # Linux测试配置
├── target/docker-registry.jar
└── pom.xml

WSL2: /home/username/docker-registry-test/
├── docker-registry.jar          # 从Windows复制
├── application-linux.yml        # Linux配置文件
└── test-scripts/               # 测试脚本
    ├── test-full-workflow.sh    # 完整流程测试
    ├── test-blob-upload.sh      # Blob上传测试
    └── test-manifest.sh         # Manifest测试
```

### 集成测试脚本

**完整流程测试脚本** (`test-scripts/test-full-workflow.sh`):
```bash
#!/bin/bash

echo "=== Docker Registry 完整流程测试 ==="

# 设置注册表地址
REGISTRY="localhost:8080"
TEST_REPO="test/alpine"
TEST_TAG="v1.0"

# 颜色输出函数
green() { echo -e "\033[32m$1\033[0m"; }
red() { echo -e "\033[31m$1\033[0m"; }
yellow() { echo -e "\033[33m$1\033[0m"; }

# 清理函数
cleanup() {
    echo "清理测试环境..."
    docker rmi ${REGISTRY}/${TEST_REPO}:${TEST_TAG} 2>/dev/null || true
    docker rmi alpine:3.18 2>/dev/null || true
}

# 错误处理
set -e
trap cleanup EXIT

echo "1. 检查Docker Registry服务..."
if ! curl -f http://localhost:8080/v2/ &>/dev/null; then
    red "❌ Registry服务未启动，请先启动服务"
    exit 1
fi
green "✅ Registry服务正常"

echo "2. 准备测试镜像..."
docker pull alpine:3.18
docker tag alpine:3.18 ${REGISTRY}/${TEST_REPO}:${TEST_TAG}
green "✅ 测试镜像准备完成"

echo "3. 测试镜像推送..."
if docker push ${REGISTRY}/${TEST_REPO}:${TEST_TAG}; then
    green "✅ 镜像推送成功"
else
    red "❌ 镜像推送失败"
    exit 1
fi

echo "4. 清理本地镜像..."
docker rmi ${REGISTRY}/${TEST_REPO}:${TEST_TAG}
docker rmi alpine:3.18
green "✅ 本地镜像清理完成"

echo "5. 测试镜像拉取..."
if docker pull ${REGISTRY}/${TEST_REPO}:${TEST_TAG}; then
    green "✅ 镜像拉取成功"
else
    red "❌ 镜像拉取失败"
    exit 1
fi

echo "6. 验证镜像完整性..."
if docker run --rm ${REGISTRY}/${TEST_REPO}:${TEST_TAG} echo "Hello Docker Registry"; then
    green "✅ 镜像运行正常"
else
    red "❌ 镜像运行失败"
    exit 1
fi

green "🎉 所有测试通过！Docker Registry功能正常"
```

**Blob上传测试脚本** (`test-scripts/test-blob-upload.sh`):
```bash
#!/bin/bash

echo "=== Blob上传功能测试 ==="

REGISTRY="localhost:8080"
TEST_REPO="test/blob"

# 创建测试文件
echo "创建测试文件..."
dd if=/dev/urandom of=/tmp/test-blob bs=1M count=5
TEST_DIGEST=$(sha256sum /tmp/test-blob | cut -d' ' -f1)
echo "测试文件SHA256: sha256:$TEST_DIGEST"

# 1. 开始上传会话
echo "1. 开始上传会话..."
UPLOAD_URL=$(curl -i -X POST "http://${REGISTRY}/v2/${TEST_REPO}/blobs/uploads/" 2>/dev/null | grep -i location | cut -d' ' -f2 | tr -d '\r')

if [ -z "$UPLOAD_URL" ]; then
    echo "❌ 无法获取上传URL"
    exit 1
fi
echo "上传URL: $UPLOAD_URL"

# 2. 上传数据
echo "2. 上传blob数据..."
curl -X PUT \
     -H "Content-Type: application/octet-stream" \
     --data-binary @/tmp/test-blob \
     "${UPLOAD_URL}?digest=sha256:${TEST_DIGEST}"

# 3. 验证blob存在性
echo "3. 验证blob存在性..."
if curl -f -I "http://${REGISTRY}/v2/${TEST_REPO}/blobs/sha256:${TEST_DIGEST}" &>/dev/null; then
    echo "✅ Blob上传验证成功"
else
    echo "❌ Blob上传验证失败"
    exit 1
fi

# 4. 下载并验证
echo "4. 下载并验证blob..."
curl -s "http://${REGISTRY}/v2/${TEST_REPO}/blobs/sha256:${TEST_DIGEST}" > /tmp/downloaded-blob
DOWNLOADED_DIGEST=$(sha256sum /tmp/downloaded-blob | cut -d' ' -f1)

if [ "$TEST_DIGEST" = "$DOWNLOADED_DIGEST" ]; then
    echo "✅ Blob下载验证成功"
else
    echo "❌ Blob下载验证失败"
    exit 1
fi

# 清理
rm -f /tmp/test-blob /tmp/downloaded-blob

echo "🎉 Blob功能测试通过！"
```

### 开发测试流程

**日常开发流程**：
```powershell
# Windows开发环境
1. # 启动开发环境
   mvn spring-boot:run -Dspring-boot.run.profiles=dev

2. # 代码修改后重新编译
   mvn clean compile
   
3. # 单元测试
   mvn test
   
4. # 打包
   mvn clean package -DskipTests
```

**Linux测试流程**：
```bash
# 1. 复制jar包到WSL2
wsl
cp /mnt/c/Users/YourName/Projects/docker-registry/target/docker-registry-*.jar ~/docker-registry-test/

# 2. 设置环境变量
export OSS_ACCESS_KEY_ID="your_key"
export OSS_ACCESS_KEY_SECRET="your_secret"

# 3. 启动服务
cd ~/docker-registry-test
java -jar docker-registry-*.jar --spring.profiles.active=linux &

# 4. 运行测试
chmod +x test-scripts/*.sh
./test-scripts/test-full-workflow.sh

# 5. 停止服务
pkill -f docker-registry
```

### 环境配置要点

**网络配置**：
```bash
# WSL2访问Windows服务的IP地址
host.docker.internal  # 固定地址，推荐使用

# 或者查看实际Windows IP
ip route show | grep default | awk '{print $3}'
```

**MySQL连接配置**：
```yaml
# Windows MySQL配置需要允许远程连接
# my.cnf 或 my.ini
[mysqld]
bind-address = 0.0.0.0

# 创建允许远程连接的用户
CREATE USER 'registry_user'@'%' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON docker_registry.* TO 'registry_user'@'%';
FLUSH PRIVILEGES;
```

**文件权限处理**：
```bash
# 确保脚本可执行
chmod +x test-scripts/*.sh

# 如果遇到Windows/Linux换行符问题
dos2unix test-scripts/*.sh
```

### 部署准备

**最终Linux部署配置** (`application-prod.yml`):
```yaml
spring:
  profiles:
    active: prod
  datasource:
    url: jdbc:mysql://your-mysql-host:3306/docker_registry
    username: registry_user
    password: ${DB_PASSWORD}
    
aliyun:
  oss:
    endpoint: https://oss-cn-hangzhou.aliyuncs.com
    bucket-name: your-docker-registry-bucket
    access-key-id: ${OSS_ACCESS_KEY_ID}
    access-key-secret: ${OSS_ACCESS_KEY_SECRET}
    blob-prefix: prod-blobs/
    temp-prefix: prod-temp/

server:
  port: 8080
  
logging:
  level:
    root: INFO
    com.registry: INFO
  file:
    name: logs/docker-registry.log
```

**部署脚本** (`deploy.sh`):
```bash
#!/bin/bash
# 部署到目标Linux环境

# 设置环境变量
export OSS_ACCESS_KEY_ID="${OSS_ACCESS_KEY_ID}"
export OSS_ACCESS_KEY_SECRET="${OSS_ACCESS_KEY_SECRET}"
export DB_PASSWORD="${DB_PASSWORD}"

# 启动服务
nohup java -jar docker-registry.jar --spring.profiles.active=prod > logs/app.log 2>&1 &
echo $! > docker-registry.pid

echo "Docker Registry已启动，PID: $(cat docker-registry.pid)"
```

### 第3-4天：Blob存储和下载功能
**目标**：实现Blob的基础操作（下载、检查存在性、SHA256校验）

**核心实体设计**：
```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Blob {
    private String digest;              // SHA256值，如：sha256:abc123...
    private Long size;                  // 文件大小（字节）
    private String ossObjectKey;        // OSS存储的对象key
    private String contentType;         // MIME类型
    private LocalDateTime createdAt;    // 创建时间
}

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Manifest {
    private Long id;                    // 自增主键
    private String digest;              // SHA256值
    private String repository;          // 仓库名
    private String tag;                 // 标签（可为空）
    private String content;             // JSON内容
    private String mediaType;           // Content-Type
    private LocalDateTime createdAt;    // 创建时间
}

@Data
@AllArgsConstructor
@NoArgsConstructor  
public class UploadSession {
    private String uuid;                // 上传会话UUID
    private String repository;          // 仓库名
    private String ossTempKey;          // OSS临时文件key
    private Long currentSize;           // 已上传字节数
    private LocalDateTime startedAt;    // 开始时间
    private LocalDateTime lastActivity; // 最后活动时间
    private String status;              // ACTIVE/COMPLETED/EXPIRED
}
```

**MyBatis Mapper接口**：
```java
@Mapper
public interface BlobMapper {
    
    @Select("SELECT * FROM blobs WHERE digest = #{digest}")
    Blob findByDigest(@Param("digest") String digest);
    
    @Select("SELECT COUNT(1) FROM blobs WHERE digest = #{digest}")
    boolean existsByDigest(@Param("digest") String digest);
    
    @Insert("INSERT INTO blobs (digest, size, oss_object_key, content_type, created_at) " +
            "VALUES (#{digest}, #{size}, #{ossObjectKey}, #{contentType}, #{createdAt})")
    int insert(Blob blob);
    
    @Delete("DELETE FROM blobs WHERE digest = #{digest}")
    int deleteByDigest(@Param("digest") String digest);
}

@Mapper 
public interface ManifestMapper {
    
    @Select("SELECT * FROM manifests WHERE repository = #{repository} AND digest = #{digest}")
    Manifest findByRepositoryAndDigest(@Param("repository") String repository, 
                                       @Param("digest") String digest);
    
    @Select("SELECT * FROM manifests WHERE repository = #{repository} AND tag = #{tag}")
    List<Manifest> findByRepositoryAndTag(@Param("repository") String repository,
                                          @Param("tag") String tag);
    
    @Insert("INSERT INTO manifests (digest, repository, tag, content, media_type, created_at) " +
            "VALUES (#{digest}, #{repository}, #{tag}, #{content}, #{mediaType}, #{createdAt})")
    int insert(Manifest manifest);
    
    @Delete("DELETE FROM manifests WHERE digest = #{digest}")
    int deleteByDigest(@Param("digest") String digest);
}

@Mapper
public interface UploadSessionMapper {
    
    @Select("SELECT * FROM upload_sessions WHERE uuid = #{uuid}")
    UploadSession findByUuid(@Param("uuid") String uuid);
    
    @Insert("INSERT INTO upload_sessions (uuid, repository, oss_temp_key, current_size, " +
            "started_at, last_activity, status) VALUES " +
            "(#{uuid}, #{repository}, #{ossTempKey}, #{currentSize}, " +
            "#{startedAt}, #{lastActivity}, #{status})")
    int insert(UploadSession session);
    
    @Update("UPDATE upload_sessions SET current_size = #{currentSize}, " +
            "last_activity = #{lastActivity} WHERE uuid = #{uuid}")
    int updateProgress(@Param("uuid") String uuid, @Param("currentSize") Long currentSize,
                       @Param("lastActivity") LocalDateTime lastActivity);
    
    @Delete("DELETE FROM upload_sessions WHERE uuid = #{uuid}")
    int deleteByUuid(@Param("uuid") String uuid);
    
    // 清理过期会话
    @Delete("DELETE FROM upload_sessions WHERE status = 'ACTIVE' AND " +
            "last_activity < #{expireTime}")
    int deleteExpiredSessions(@Param("expireTime") LocalDateTime expireTime);
}
```

**OSS存储服务**：
```java
@Service
public class OssStorageService {
    
    @Autowired
    private OSS ossClient;
    
    @Autowired  
    private OssProperties ossProperties;
    
    /**
     * 生成blob的OSS存储key
     */
    public String generateBlobKey(String digest) {
        // sha256:abc123def456... -> blobs/ab/abc123def456.../data
        String hash = digest.substring(7); // 移除"sha256:"前缀
        String prefix = hash.substring(0, 2); // 前2位作为目录
        return String.format("%s%s/%s/data", ossProperties.getBlobPrefix(), prefix, hash);
    }
    
    /**
     * 生成临时文件的OSS key
     */
    public String generateTempKey(String uuid) {
        return ossProperties.getTempPrefix() + uuid + ".tmp";
    }
    
    /**
     * 上传数据到OSS
     */
    public void putObject(String key, InputStream inputStream, long contentLength) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(contentLength);
        ossClient.putObject(ossProperties.getBucketName(), key, inputStream, metadata);
    }
    
    /**
     * 获取OSS对象的输入流
     */
    public InputStream getObjectInputStream(String key) {
        OSSObject ossObject = ossClient.getObject(ossProperties.getBucketName(), key);
        return ossObject.getObjectContent();
    }
    
    /**
     * 检查OSS对象是否存在
     */
    public boolean doesObjectExist(String key) {
        return ossClient.doesObjectExist(ossProperties.getBucketName(), key);
    }
    
    /**
     * 删除OSS对象
     */
    public void deleteObject(String key) {
        ossClient.deleteObject(ossProperties.getBucketName(), key);
    }
    
    /**
     * 复制OSS对象（临时文件移动到正式位置）
     */
    public void copyObject(String sourceKey, String destKey) {
        CopyObjectRequest copyRequest = new CopyObjectRequest(
            ossProperties.getBucketName(), sourceKey,
            ossProperties.getBucketName(), destKey
        );
        ossClient.copyObject(copyRequest);
    }
    
    /**
     * 追加写入OSS对象（用于分片上传）
     */
    public AppendObjectResult appendObject(String key, InputStream inputStream, 
                                           long position) throws IOException {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(inputStream.available());
        
        AppendObjectRequest appendRequest = new AppendObjectRequest(
            ossProperties.getBucketName(), key, inputStream, metadata
        );
        appendRequest.setPosition(position);
        
        return ossClient.appendObject(appendRequest);
    }
}
```

**Blob下载实现**：
```java
@RestController
public class BlobController {
    
    @Autowired
    private BlobMapper blobMapper;
    
    @Autowired
    private OssStorageService ossStorageService;
    
    // GET /v2/{name}/blobs/{digest}
    @GetMapping("/v2/{name}/blobs/{digest}")
    public ResponseEntity<StreamingResponseBody> getBlob(
            @PathVariable String name,
            @PathVariable String digest,
            @RequestHeader(value = "Range", required = false) String range) {
        
        // 验证digest格式
        if (!DigestUtils.isValidDigest(digest)) {
            return ResponseEntity.badRequest().build();
        }
        
        // 查找blob
        Blob blob = blobMapper.findByDigest(digest);
        if (blob == null) {
            return ResponseEntity.notFound().build();
        }
        
        // 检查OSS中文件是否存在
        if (!ossStorageService.doesObjectExist(blob.getOssObjectKey())) {
            return ResponseEntity.notFound().build();
        }
        
        // 创建流式响应
        StreamingResponseBody responseBody = outputStream -> {
            try (InputStream inputStream = ossStorageService.getObjectInputStream(blob.getOssObjectKey())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }
        };
        
        return ResponseEntity.ok()
            .header("Content-Type", blob.getContentType())
            .header("Docker-Content-Digest", digest)
            .header("Content-Length", blob.getSize().toString())
            .body(responseBody);
    }
    
    // HEAD /v2/{name}/blobs/{digest}
    @RequestMapping(value = "/v2/{name}/blobs/{digest}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headBlob(@PathVariable String name, @PathVariable String digest) {
        
        if (!DigestUtils.isValidDigest(digest)) {
            return ResponseEntity.badRequest().build();
        }
        
        Blob blob = blobMapper.findByDigest(digest);
        if (blob != null && ossStorageService.doesObjectExist(blob.getOssObjectKey())) {
            return ResponseEntity.ok()
                .header("Docker-Content-Digest", digest)
                .header("Content-Length", blob.getSize().toString())
                .build();
        }
        return ResponseEntity.notFound().build();
    }
}
```

**文件存储策略**：
```
storage/blobs/
  └── sha256/
      └── ab/                    # digest前2位作为目录
          └── abc123def456.../   # 完整digest
              └── data           # 实际文件
```

**产出**：
- [ ] 完成Blob下载和存在性检查
- [ ] 实现文件存储服务
- [ ] 建立SHA256校验机制

### 第5-8天：Blob分片上传系统（核心难点）
**目标**：实现完整的分片上传功能，这是最复杂的部分

**上传会话管理**：
```java
@Entity
@Table(name = "upload_sessions")
public class UploadSession {
    @Id
    private String uuid;                // 上传会话ID
    
    @Column(nullable = false)
    private String repository;          // 仓库名
    
    @Column(name = "temp_file_path")
    private String tempFilePath;        // 临时文件路径
    
    private Long currentSize;           // 当前已上传大小
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "last_activity")
    private LocalDateTime lastActivity;
    
    @Enumerated(EnumType.STRING)
    private UploadStatus status;        // ACTIVE, COMPLETED, EXPIRED
}
```

**分片上传实现**：
```java
@RestController
public class UploadController {
    
    // POST /v2/{name}/blobs/uploads/ - 开始上传
    @PostMapping("/v2/{name}/blobs/uploads/")
    public ResponseEntity<Void> startUpload(@PathVariable String name) {
        String uuid = UUID.randomUUID().toString();
        
        UploadSession session = new UploadSession();
        session.setUuid(uuid);
        session.setRepository(name);
        session.setCurrentSize(0L);
        session.setStartedAt(LocalDateTime.now());
        session.setStatus(UploadStatus.ACTIVE);
        
        // 创建临时文件
        String tempPath = storageService.createTempFile(uuid);
        session.setTempFilePath(tempPath);
        
        uploadSessionRepository.save(session);
        
        String location = String.format("/v2/%s/blobs/uploads/%s", name, uuid);
        return ResponseEntity.accepted()
            .header("Location", location)
            .header("Range", "0-0")
            .build();
    }
    
    // PATCH /v2/{name}/blobs/uploads/{uuid} - 分片上传
    @PatchMapping("/v2/{name}/blobs/uploads/{uuid}")
    public ResponseEntity<Void> uploadChunk(
            @PathVariable String name,
            @PathVariable String uuid,
            @RequestHeader("Content-Range") String contentRange,
            HttpServletRequest request) throws IOException {
        
        // 查找上传会话
        UploadSession session = uploadSessionRepository.findById(uuid)
            .orElse(null);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        
        // 解析Content-Range头
        RangeInfo range = parseContentRange(contentRange);
        if (range.getStart() != session.getCurrentSize()) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .build();
        }
        
        // 写入数据到临时文件
        long bytesWritten = storageService.appendToFile(
            session.getTempFilePath(), 
            request.getInputStream()
        );
        
        // 更新会话状态
        session.setCurrentSize(session.getCurrentSize() + bytesWritten);
        session.setLastActivity(LocalDateTime.now());
        uploadSessionRepository.save(session);
        
        String location = String.format("/v2/%s/blobs/uploads/%s", name, uuid);
        return ResponseEntity.accepted()
            .header("Location", location)
            .header("Range", String.format("0-%d", session.getCurrentSize() - 1))
            .build();
    }
    
    // PUT /v2/{name}/blobs/uploads/{uuid} - 完成上传
    @PutMapping("/v2/{name}/blobs/uploads/{uuid}")
    public ResponseEntity<Void> completeUpload(
            @PathVariable String name,
            @PathVariable String uuid,
            @RequestParam("digest") String expectedDigest) throws IOException {
        
        UploadSession session = uploadSessionRepository.findById(uuid)
            .orElse(null);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        
        // 计算实际SHA256
        String actualDigest = storageService.calculateDigest(session.getTempFilePath());
        if (!expectedDigest.equals(actualDigest)) {
            return ResponseEntity.badRequest().build();
        }
        
        // 移动到正式存储位置
        String finalPath = storageService.moveToFinalLocation(
            session.getTempFilePath(), 
            actualDigest
        );
        
        // 保存Blob记录
        Blob blob = new Blob();
        blob.setDigest(actualDigest);
        blob.setSize(session.getCurrentSize());
        blob.setFilePath(finalPath);
        blob.setCreatedAt(LocalDateTime.now());
        blobRepository.save(blob);
        
        // 清理上传会话
        uploadSessionRepository.delete(session);
        
        String location = String.format("/v2/%s/blobs/%s", name, actualDigest);
        return ResponseEntity.created(URI.create(location))
            .header("Docker-Content-Digest", actualDigest)
            .build();
    }
}
```

**产出**：
- [ ] 完成分片上传完整功能
- [ ] 实现上传会话管理
- [ ] 建立临时文件处理机制

### 第9-11天：Manifest操作
**目标**：实现Manifest的获取、上传和Manifest List管理

**Manifest实体**：
```java
@Entity
@Table(name = "manifests")
public class Manifest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String digest;              // SHA256值
    
    @Column(nullable = false)
    private String repository;          // 仓库名
    
    private String tag;                 // 标签（可为空）
    
    @Column(columnDefinition = "TEXT")
    private String content;             // JSON内容
    
    @Column(name = "media_type")
    private String mediaType;           // content-type
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
```

**Manifest操作实现**：
```java
@RestController
public class ManifestController {
    
    // GET /v2/{name}/manifests/{reference}
    @GetMapping("/v2/{name}/manifests/{reference}")
    public ResponseEntity<String> getManifest(
            @PathVariable String name,
            @PathVariable String reference,
            @RequestHeader(value = "Accept", required = false) String accept) {
        
        if (isDigest(reference)) {
            // 通过digest获取具体manifest
            Optional<Manifest> manifest = manifestRepository
                .findByRepositoryAndDigest(name, reference);
            if (manifest.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok()
                .header("Content-Type", manifest.get().getMediaType())
                .header("Docker-Content-Digest", manifest.get().getDigest())
                .body(manifest.get().getContent());
        } else {
            // 通过tag获取manifest list
            return getManifestListByTag(name, reference, accept);
        }
    }
    
    // PUT /v2/{name}/manifests/{reference}
    @PutMapping("/v2/{name}/manifests/{reference}")
    public ResponseEntity<Void> putManifest(
            @PathVariable String name,
            @PathVariable String reference,
            @RequestBody String manifestContent,
            @RequestHeader("Content-Type") String contentType) throws IOException {
        
        // 解析manifest内容
        ManifestDto manifestDto = objectMapper.readValue(manifestContent, ManifestDto.class);
        
        // 检查依赖的blob是否存在
        for (String digest : extractBlobDigests(manifestDto)) {
            if (!blobRepository.existsByDigest(digest)) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
            }
        }
        
        // 计算manifest的digest
        String manifestDigest = calculateManifestDigest(manifestContent);
        
        // 保存manifest
        Manifest manifest = new Manifest();
        manifest.setRepository(name);
        manifest.setDigest(manifestDigest);
        manifest.setContent(manifestContent);
        manifest.setMediaType(contentType);
        manifest.setCreatedAt(LocalDateTime.now());
        
        // 如果reference是tag，设置tag字段
        if (!isDigest(reference)) {
            manifest.setTag(reference);
        }
        
        manifestRepository.save(manifest);
        
        // 更新或创建manifest list
        updateManifestList(name, reference, manifestDigest, manifestDto);
        
        String location = String.format("/v2/%s/manifests/%s", name, manifestDigest);
        return ResponseEntity.created(URI.create(location))
            .header("Docker-Content-Digest", manifestDigest)
            .build();
    }
}
```

**产出**：
- [ ] 完成Manifest获取和上传
- [ ] 实现Manifest List自动管理
- [ ] 支持两种Content-Type

### 第12-13天：错误处理和集成测试
**目标**：完善错误处理，准备集成测试

**标准错误响应**：
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
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
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        ErrorResponse error = new ErrorResponse();
        error.setCode("INVALID_REQUEST");
        error.setMessage("invalid request format");
        error.setDetail(e.getMessage());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .header("Content-Type", "application/json")
            .body(error);
    }
}
```

**集成测试准备**：
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application-test.properties")
class DockerRegistryIntegrationTest {
    
    @Test
    void testFullPushPullWorkflow() throws Exception {
        // 1. 上传blob
        String blobDigest = uploadTestBlob();
        
        // 2. 上传manifest
        String manifestDigest = uploadTestManifest(blobDigest);
        
        // 3. 验证可以下载
        verifyBlobDownload(blobDigest);
        verifyManifestDownload(manifestDigest);
    }
}
```

**产出**：
- [ ] 完善错误处理机制
- [ ] 编写集成测试用例

### 第14天：部署和调试
**目标**：准备Linux部署，通过出题人测试

**Linux部署准备**：
```dockerfile
FROM openjdk:17-jre-slim

WORKDIR /app
COPY target/docker-registry-*.jar app.jar
RUN mkdir -p /app/storage/blobs /app/storage/temp

EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
```

**打包脚本**：
```bash
#!/bin/bash
# build.sh
mvn clean package -DskipTests
docker build -t docker-registry:latest .
```

**产出**：
- [ ] 完成Linux环境部署
- [ ] 通过集成测试

---

## 关键技术实现要点

### 1. SHA256校验机制
```java
public class DigestUtils {
    public static String calculateSHA256(File file) throws IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return "sha256:" + Hex.encodeHexString(digest.digest());
    }
}
```

### 2. Content-Type处理
```java
// 支持的Manifest类型
private static final String MANIFEST_V2_TYPE = "application/vnd.docker.distribution.manifest.v2+json";
private static final String MANIFEST_LIST_V2_TYPE = "application/vnd.docker.distribution.manifest.list.v2+json";

private boolean isSupportedManifestType(String contentType) {
    return MANIFEST_V2_TYPE.equals(contentType) || MANIFEST_LIST_V2_TYPE.equals(contentType);
}
```

### 3. 文件存储优化
```java
private String generateBlobPath(String digest) {
    // sha256:abc123def456... -> blobs/sha256/ab/abc123def456.../data
    String hash = digest.substring(7); // 移除"sha256:"前缀
    String prefix = hash.substring(0, 2);
    return String.format("blobs/sha256/%s/%s/data", prefix, hash);
}
```

---

## 成功标准

### 最低要求（必须完成）
- [ ] 实现所有11个API端点
- [ ] 正确处理分片上传
- [ ] SHA256完整性校验
- [ ] 基础错误处理（400、404）
- [ ] 支持两种Manifest Content-Type
- [ ] 通过出题人集成测试

### 加分项
- [ ] Range请求支持
- [ ] 上传会话超时清理
- [ ] 并发安全处理
- [ ] 完善的日志记录

---

## 风险控制

### 时间风险
- **第1周**：必须完成Blob和上传功能
- **第2周**：完成Manifest功能
- **第3周**：测试和部署

### 技术风险  
1. **分片上传复杂度** - 提前详细研究API文档
2. **Manifest List管理** - 理解tag和digest的关系
3. **Linux部署问题** - 提前在Linux环境测试

### 调试策略
1. 使用真实Docker客户端测试
2. 对比官方Registry的响应格式
3. 详细日志记录每个API调用

---

这个方案相比原学习计划更加聚焦和实用，基于您的Java Spring Boot经验，应该可以在2-3周内完成核心功能并通过测试。关键是要循序渐进，先实现基础功能再完善高级特性。

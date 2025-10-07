# Docker Registry 快速启动指南

## 📋 技术栈版本

| 组件 | 版本 |
|------|------|
| **Java** | JDK 17 |
| **Spring Boot** | 3.2.0 |
| **MyBatis** | 3.0.3 |
| **MySQL** | 8.0+ |
| **Maven** | 3.8+ |
| **阿里云OSS SDK** | 3.17.4 |

## 📁 项目架构

```
src/main/
├── java/com/diy/
│   ├── Diy.java                          # 启动类
│   ├── config/                           # 配置类
│   │   ├── OssConfig.java                # OSS客户端配置
│   │   ├── OssProperties.java            # OSS属性配置
│   │   ├── RegistryProperties.java       # Registry配置
│   │   └── WebConfig.java                # Web配置
│   ├── controller/                       # 控制器层（REST API）
│   │   ├── BlobController.java           # Blob下载/检查
│   │   ├── ManifestController.java       # Manifest管理
│   │   ├── RegistryController.java       # 基础API
│   │   └── UploadController.java         # 分片上传
│   ├── service/                          # 业务服务层
│   │   ├── BlobService.java              # Blob服务接口
│   │   ├── ManifestService.java          # Manifest服务接口
│   │   ├── UploadService.java            # 上传服务接口
│   │   ├── OssStorageService.java        # OSS存储服务
│   │   └── impl/                         # 服务实现类
│   │       ├── BlobServiceImpl.java
│   │       ├── ManifestServiceImpl.java
│   │       └── UploadServiceImpl.java
│   ├── mapper/                           # 数据访问层（MyBatis）
│   │   ├── BlobMapper.java
│   │   ├── ManifestMapper.java
│   │   └── UploadSessionMapper.java
│   ├── entity/                           # 实体类
│   │   ├── Blob.java                     # Blob实体
│   │   ├── Manifest.java                 # Manifest实体
│   │   └── UploadSession.java            # 上传会话实体
│   ├── dto/                              # 数据传输对象
│   │   ├── ErrorResponse.java            # 错误响应
│   │   └── ManifestDto.java              # Manifest DTO
│   ├── exception/                        # 异常处理
│   │   ├── GlobalExceptionHandler.java   # 全局异常处理器
│   │   ├── BlobNotFoundException.java
│   │   ├── ManifestNotFoundException.java
│   │   ├── InvalidDigestException.java
│   │   ├── UnsupportedMediaTypeException.java
│   │   └── UploadSessionNotFoundException.java
│   └── utils/                            # 工具类
│       ├── DigestUtils.java              # SHA256计算
│       └── RangeUtils.java               # Range请求处理
│
└── resources/
    ├── application.yml                   # 主配置文件
    ├── application-local.yml             # 本地开发配置（含密钥，不提交Git）
    ├── application-dev.yml               # 开发环境配置（使用环境变量）
    ├── application-linux.yml             # Linux环境配置（使用环境变量）
    └── mapper/                           # MyBatis XML映射
        ├── BlobMapper.xml
        ├── ManifestMapper.xml
        └── UploadSessionMapper.xml
```

## 🚀 本地启动

### 1. 数据库初始化
```bash
mysql -u root -p < sql/diy.sql
```

### 2. 配置密钥
确保 `src/main/resources/application-local.yml` 中已配置阿里云OSS密钥

### 3. 启动服务
```bash
# Maven启动
mvn spring-boot:run

# 或使用jar包
mvn clean package -DskipTests
java -jar target/docker-it-yourself-0.0.1-SNAPSHOT.jar
```

### 4. 验证
```bash
curl http://localhost:8080/v2/
# 预期返回: {}
```

## 🐧 Linux环境部署

已打包jar文件：`docker-it-yourself-0.0.1-SNAPSHOT.jar`（使用local配置打包，已包含OSS密钥）

直接启动即可：
```bash
java -jar docker-it-yourself-0.0.1-SNAPSHOT.jar
```

---

**更多详细信息请参考项目文档和源代码注释**


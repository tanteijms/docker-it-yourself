package com.diy.service;

import com.diy.dto.ManifestDto;
import com.diy.entity.Manifest;

import java.util.List;

/**
 * Manifest业务服务接口
 * 处理Docker镜像元数据的业务操作
 * 
 * @author diy
 */
public interface ManifestService {

    /**
     * 根据仓库名和引用获取manifest
     * 
     * @param repository 仓库名
     * @param reference  引用（tag或digest）
     * @return Manifest实体
     * @throws com.diy.exception.ManifestNotFoundException 当manifest不存在时
     */
    Manifest getManifest(String repository, String reference);

    /**
     * 检查manifest是否存在
     * 
     * @param repository 仓库名
     * @param reference  引用（tag或digest）
     * @return 是否存在
     */
    boolean existsManifest(String repository, String reference);

    /**
     * 上传新的manifest
     * 
     * @param repository      仓库名
     * @param reference       引用（通常是tag）
     * @param manifestContent manifest JSON内容
     * @param mediaType       媒体类型
     * @return 创建的Manifest实体
     * @throws com.diy.exception.BlobNotFoundException 当依赖的blob不存在时
     * @throws IllegalArgumentException                当manifest格式无效时
     */
    Manifest putManifest(String repository, String reference, String manifestContent, String mediaType);

    /**
     * 删除manifest
     * 
     * @param repository 仓库名
     * @param reference  引用（tag或digest）
     * @return 是否删除成功
     */
    boolean deleteManifest(String repository, String reference);

    /**
     * 获取仓库的所有tag
     * 
     * @param repository 仓库名
     * @return tag列表
     */
    List<String> getRepositoryTags(String repository);

    /**
     * 构建manifest list（当通过tag访问时）
     * 
     * @param repository 仓库名
     * @param tag        标签
     * @return manifest list JSON内容
     */
    String buildManifestList(String repository, String tag);

    /**
     * 验证manifest的依赖完整性
     * 
     * @param manifestDto manifest DTO
     * @return 验证结果
     */
    ManifestValidationResult validateManifestDependencies(ManifestDto manifestDto);

    /**
     * 计算manifest的digest
     * 
     * @param manifestContent manifest JSON内容
     * @return SHA256 digest
     */
    String calculateManifestDigest(String manifestContent);

    /**
     * 判断引用是否为digest格式
     * 
     * @param reference 引用
     * @return 是否为digest格式
     */
    boolean isDigestReference(String reference);

    /**
     * 获取manifest统计信息
     * 
     * @param repository 仓库名（可选）
     * @return 统计信息
     */
    ManifestStats getManifestStats(String repository);

    /**
     * Manifest验证结果
     */
    interface ManifestValidationResult {
        boolean isValid();

        List<String> getMissingBlobs();

        String getErrorMessage();
    }

    /**
     * Manifest统计信息
     */
    interface ManifestStats {
        long getTotalCount();

        long getRepositoryCount();

        long getTagCount();
    }
}

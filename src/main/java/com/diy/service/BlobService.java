package com.diy.service;

import com.diy.entity.Blob;

import java.io.IOException;
import java.io.InputStream;

/**
 * Blob业务服务接口
 * 定义Docker镜像层数据的业务操作
 * 
 * @author diy
 */
public interface BlobService {

    /**
     * 根据digest获取blob
     * 
     * @param digest SHA256值
     * @return Blob实体
     * @throws com.diy.exception.BlobNotFoundException 当blob不存在时
     */
    Blob getBlobByDigest(String digest);

    /**
     * 检查blob是否存在
     * 
     * @param digest SHA256值
     * @return 是否存在
     */
    boolean existsByDigest(String digest);

    /**
     * 获取blob的输入流（用于下载）
     * 
     * @param digest SHA256值
     * @return 输入流
     * @throws IOException                             IO异常
     * @throws com.diy.exception.BlobNotFoundException 当blob不存在时
     */
    InputStream getBlobInputStream(String digest) throws IOException;

    /**
     * 获取blob大小
     * 
     * @param digest SHA256值
     * @return 文件大小（字节）
     * @throws com.diy.exception.BlobNotFoundException 当blob不存在时
     */
    long getBlobSize(String digest);

    /**
     * 创建新的blob记录（完成上传后调用）
     * 
     * @param digest       SHA256值
     * @param size         文件大小
     * @param ossObjectKey OSS存储key
     * @param contentType  MIME类型
     * @return 创建的Blob实体
     */
    Blob createBlob(String digest, long size, String ossObjectKey, String contentType);

    /**
     * 删除blob
     * 
     * @param digest SHA256值
     * @return 是否删除成功
     */
    boolean deleteBlob(String digest);

    /**
     * 验证blob的完整性
     * 
     * @param digest SHA256值
     * @return 是否完整
     * @throws IOException IO异常
     */
    boolean validateBlobIntegrity(String digest) throws IOException;

    /**
     * 获取blob统计信息
     * 
     * @return 统计信息（总数、总大小等）
     */
    BlobStats getBlobStats();

    /**
     * Blob统计信息DTO
     */
    interface BlobStats {
        long getTotalCount();

        long getTotalSize();

        String getFormattedSize();
    }
}

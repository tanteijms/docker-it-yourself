package com.diy.mapper;

import com.diy.entity.Blob;
import org.apache.ibatis.annotations.*;

/**
 * Blob数据访问层
 * 
 * @author diy
 */
@Mapper
public interface BlobMapper {

    /**
     * 根据digest查找blob
     * 
     * @param digest SHA256值
     * @return Blob实体
     */
    @Select("SELECT digest, size, oss_object_key, content_type, created_at " +
            "FROM blobs WHERE digest = #{digest}")
    Blob findByDigest(@Param("digest") String digest);

    /**
     * 检查blob是否存在
     * 
     * @param digest SHA256值
     * @return 是否存在
     */
    @Select("SELECT COUNT(1) > 0 FROM blobs WHERE digest = #{digest}")
    boolean existsByDigest(@Param("digest") String digest);

    /**
     * 插入新的blob记录
     * 
     * @param blob Blob实体
     * @return 影响行数
     */
    @Insert("INSERT INTO blobs (digest, size, oss_object_key, content_type, created_at) " +
            "VALUES (#{digest}, #{size}, #{ossObjectKey}, #{contentType}, #{createdAt})")
    int insert(Blob blob);

    /**
     * 根据digest删除blob
     * 
     * @param digest SHA256值
     * @return 影响行数
     */
    @Delete("DELETE FROM blobs WHERE digest = #{digest}")
    int deleteByDigest(@Param("digest") String digest);

    /**
     * 获取blob总数
     * 
     * @return 总数
     */
    @Select("SELECT COUNT(1) FROM blobs")
    long countAll();

    /**
     * 获取所有blob的总大小
     * 
     * @return 总大小（字节）
     */
    @Select("SELECT COALESCE(SUM(size), 0) FROM blobs")
    long sumSize();
}

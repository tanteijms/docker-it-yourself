package com.diy.mapper;

import com.diy.entity.Manifest;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Manifest数据访问层
 * 
 * @author diy
 */
@Mapper
public interface ManifestMapper {

    /**
     * 根据仓库名和digest查找manifest
     * 
     * @param repository 仓库名
     * @param digest     SHA256值
     * @return Manifest实体
     */
    @Select("SELECT id, digest, repository, tag, content, media_type, created_at " +
            "FROM manifests WHERE repository = #{repository} AND digest = #{digest}")
    Manifest findByRepositoryAndDigest(@Param("repository") String repository,
            @Param("digest") String digest);

    /**
     * 根据仓库名和tag查找manifest列表
     * 
     * @param repository 仓库名
     * @param tag        标签
     * @return Manifest列表
     */
    @Select("SELECT id, digest, repository, tag, content, media_type, created_at " +
            "FROM manifests WHERE repository = #{repository} AND tag = #{tag} " +
            "ORDER BY created_at DESC")
    List<Manifest> findByRepositoryAndTag(@Param("repository") String repository,
            @Param("tag") String tag);

    /**
     * 根据仓库名查找所有manifest
     * 
     * @param repository 仓库名
     * @return Manifest列表
     */
    @Select("SELECT id, digest, repository, tag, content, media_type, created_at " +
            "FROM manifests WHERE repository = #{repository} " +
            "ORDER BY created_at DESC")
    List<Manifest> findByRepository(@Param("repository") String repository);

    /**
     * 检查manifest是否存在
     * 
     * @param repository 仓库名
     * @param digest     SHA256值
     * @return 是否存在
     */
    @Select("SELECT COUNT(1) > 0 FROM manifests " +
            "WHERE repository = #{repository} AND digest = #{digest}")
    boolean existsByRepositoryAndDigest(@Param("repository") String repository,
            @Param("digest") String digest);

    /**
     * 插入新的manifest记录
     * 
     * @param manifest Manifest实体
     * @return 影响行数
     */
    @Insert("INSERT INTO manifests (digest, repository, tag, content, media_type, created_at) " +
            "VALUES (#{digest}, #{repository}, #{tag}, #{content}, #{mediaType}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Manifest manifest);

    /**
     * 根据digest删除manifest
     * 
     * @param digest SHA256值
     * @return 影响行数
     */
    @Delete("DELETE FROM manifests WHERE digest = #{digest}")
    int deleteByDigest(@Param("digest") String digest);

    /**
     * 根据仓库名和tag删除manifest
     * 
     * @param repository 仓库名
     * @param tag        标签
     * @return 影响行数
     */
    @Delete("DELETE FROM manifests WHERE repository = #{repository} AND tag = #{tag}")
    int deleteByRepositoryAndTag(@Param("repository") String repository,
            @Param("tag") String tag);

    /**
     * 获取仓库的所有tag
     * 
     * @param repository 仓库名
     * @return tag列表
     */
    @Select("SELECT DISTINCT tag FROM manifests " +
            "WHERE repository = #{repository} AND tag IS NOT NULL " +
            "ORDER BY tag")
    List<String> findTagsByRepository(@Param("repository") String repository);
}

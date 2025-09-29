package com.diy.mapper;

import com.diy.entity.UploadSession;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 上传会话数据访问层
 * 
 * @author diy
 */
@Mapper
public interface UploadSessionMapper {

    /**
     * 根据UUID查找上传会话
     * 
     * @param uuid 会话UUID
     * @return 上传会话实体
     */
    @Select("SELECT uuid, repository, oss_temp_key, current_size, " +
            "started_at, last_activity, status " +
            "FROM upload_sessions WHERE uuid = #{uuid}")
    UploadSession findByUuid(@Param("uuid") String uuid);

    /**
     * 插入新的上传会话
     * 
     * @param session 上传会话实体
     * @return 影响行数
     */
    @Insert("INSERT INTO upload_sessions (uuid, repository, oss_temp_key, current_size, " +
            "started_at, last_activity, status) VALUES " +
            "(#{uuid}, #{repository}, #{ossTempKey}, #{currentSize}, " +
            "#{startedAt}, #{lastActivity}, #{status})")
    int insert(UploadSession session);

    /**
     * 更新上传进度
     * 
     * @param uuid         会话UUID
     * @param currentSize  当前大小
     * @param lastActivity 最后活动时间
     * @return 影响行数
     */
    @Update("UPDATE upload_sessions SET current_size = #{currentSize}, " +
            "last_activity = #{lastActivity} WHERE uuid = #{uuid}")
    int updateProgress(@Param("uuid") String uuid,
            @Param("currentSize") Long currentSize,
            @Param("lastActivity") LocalDateTime lastActivity);

    /**
     * 更新会话状态
     * 
     * @param uuid         会话UUID
     * @param status       状态
     * @param lastActivity 最后活动时间
     * @return 影响行数
     */
    @Update("UPDATE upload_sessions SET status = #{status}, " +
            "last_activity = #{lastActivity} WHERE uuid = #{uuid}")
    int updateStatus(@Param("uuid") String uuid,
            @Param("status") String status,
            @Param("lastActivity") LocalDateTime lastActivity);

    /**
     * 根据UUID删除上传会话
     * 
     * @param uuid 会话UUID
     * @return 影响行数
     */
    @Delete("DELETE FROM upload_sessions WHERE uuid = #{uuid}")
    int deleteByUuid(@Param("uuid") String uuid);

    /**
     * 查找过期的活跃会话
     * 
     * @param expireTime 过期时间点
     * @return 过期会话列表
     */
    @Select("SELECT uuid, repository, oss_temp_key, current_size, " +
            "started_at, last_activity, status " +
            "FROM upload_sessions WHERE status = 'ACTIVE' " +
            "AND last_activity < #{expireTime}")
    List<UploadSession> findExpiredActiveSessions(@Param("expireTime") LocalDateTime expireTime);

    /**
     * 清理过期会话
     * 
     * @param expireTime 过期时间点
     * @return 删除的记录数
     */
    @Delete("DELETE FROM upload_sessions WHERE status = 'ACTIVE' " +
            "AND last_activity < #{expireTime}")
    int deleteExpiredSessions(@Param("expireTime") LocalDateTime expireTime);

    /**
     * 根据仓库名查找活跃会话
     * 
     * @param repository 仓库名
     * @return 活跃会话列表
     */
    @Select("SELECT uuid, repository, oss_temp_key, current_size, " +
            "started_at, last_activity, status " +
            "FROM upload_sessions WHERE repository = #{repository} " +
            "AND status = 'ACTIVE' ORDER BY started_at DESC")
    List<UploadSession> findActiveSessionsByRepository(@Param("repository") String repository);

    /**
     * 获取活跃会话总数
     * 
     * @return 活跃会话数量
     */
    @Select("SELECT COUNT(1) FROM upload_sessions WHERE status = 'ACTIVE'")
    long countActiveSessions();
}

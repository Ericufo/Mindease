package com.mindease.mapper;

import com.mindease.pojo.entity.MoodLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MoodLogMapper {

    /**
     * 查询用户最近的情绪日志
     *
     * @param userId
     * @param startDate
     * @return
     */
    @Select("select * from mood_log where user_id = #{userId} and log_date >= #{startDate} order by log_date desc")
    List<MoodLog> getRecentMoodLogs(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate);

    /**
     * 检查用户是否有情绪日志
     *
     * @param userId
     * @return
     */
    @Select("select count(*) from mood_log where user_id = #{userId}")
    int countByUserId(Long userId);
}


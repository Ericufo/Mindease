package com.mindease.mapper;

import com.mindease.pojo.entity.AssessmentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AssessmentRecordMapper {

    /**
     * 查询用户最近的测评记录
     *
     * @param userId
     * @return
     */
    @Select("select * from assessment_record where user_id = #{userId} order by create_time desc limit 1")
    AssessmentRecord getLatestByUserId(Long userId);

    /**
     * 检查用户是否有测评记录
     *
     * @param userId
     * @return
     */
    @Select("select count(*) from assessment_record where user_id = #{userId}")
    int countByUserId(Long userId);
}


package com.mindease.mapper;

import com.mindease.pojo.entity.Appointment;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AppointmentMapper {

    /**
     * 插入预约
     *
     * @param appointment
     */
    @Insert("insert into appointment(user_id, counselor_id, start_time, end_time, status, user_note, create_time, update_time) " +
            "values(#{userId}, #{counselorId}, #{startTime}, #{endTime}, #{status}, #{userNote}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Appointment appointment);

    /**
     * 根据ID查询预约
     *
     * @param id
     * @return
     */
    @Select("select * from appointment where id = #{id}")
    Appointment getById(Long id);

    /**
     * 查询咨询师在指定时段的预约数量
     *
     * @param counselorId
     * @param startTime
     * @param endTime
     * @return
     */
    @Select("select count(*) from appointment where counselor_id = #{counselorId} " +
            "and status in ('PENDING', 'CONFIRMED') " +
            "and ((start_time < #{endTime} and end_time > #{startTime}))")
    int countByTimeRange(@Param("counselorId") Long counselorId,
                         @Param("startTime") LocalDateTime startTime,
                         @Param("endTime") LocalDateTime endTime);

    /**
     * 查询咨询师某天的所有预约
     *
     * @param counselorId
     * @param dayStart
     * @param dayEnd
     * @return
     */
    @Select("select * from appointment where counselor_id = #{counselorId} " +
            "and status in ('PENDING', 'CONFIRMED') " +
            "and start_time >= #{dayStart} and start_time < #{dayEnd}")
    List<Appointment> getByDate(@Param("counselorId") Long counselorId,
                                 @Param("dayStart") LocalDateTime dayStart,
                                 @Param("dayEnd") LocalDateTime dayEnd);

    /**
     * 查询用户的预约列表
     *
     * @param userId
     * @param status
     * @param limit
     * @param offset
     * @return
     */
    List<Appointment> getByUserId(@Param("userId") Long userId,
                                   @Param("status") String status,
                                   @Param("limit") Integer limit,
                                   @Param("offset") Integer offset);

    /**
     * 查询咨询师的预约列表
     *
     * @param counselorId
     * @param status
     * @param limit
     * @param offset
     * @return
     */
    List<Appointment> getByCounselorId(@Param("counselorId") Long counselorId,
                                        @Param("status") String status,
                                        @Param("limit") Integer limit,
                                        @Param("offset") Integer offset);

    /**
     * 查询预约总数
     *
     * @param userId
     * @param counselorId
     * @param status
     * @return
     */
    int count(@Param("userId") Long userId,
              @Param("counselorId") Long counselorId,
              @Param("status") String status);

    /**
     * 更新预约状态
     *
     * @param id
     * @param status
     * @param cancelReason
     * @param updateTime
     */
    @Update("update appointment set status = #{status}, " +
            "cancel_reason = #{cancelReason}, update_time = #{updateTime} where id = #{id}")
    void updateStatus(@Param("id") Long id,
                      @Param("status") String status,
                      @Param("cancelReason") String cancelReason,
                      @Param("updateTime") LocalDateTime updateTime);

    /**
     * 查询已过期的已确认预约（用于自动完成）
     * 条件：状态为CONFIRMED且结束时间早于当前时间
     *
     * @param userId 用户ID（可选，为null则查询所有用户）
     * @param currentTime 当前时间
     * @return
     */
    List<Appointment> getExpiredConfirmedAppointments(@Param("userId") Long userId, 
                                                       @Param("currentTime") LocalDateTime currentTime);
}


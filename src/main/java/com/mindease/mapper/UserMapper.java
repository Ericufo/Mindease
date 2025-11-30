package com.mindease.mapper;

import com.mindease.pojo.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户
     */
    @Select("select * from sys_user where username = #{username}")
    User getByUsername(String username);

}

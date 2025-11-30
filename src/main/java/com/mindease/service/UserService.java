package com.mindease.service;

import com.mindease.pojo.dto.UserLoginDTO;
import com.mindease.pojo.entity.User;

public interface UserService {

    /**
     * 登录
     *
     * @param userLoginDTO
     * @param needAdmin    是否需要管理员权限
     * @return
     */
    User login(UserLoginDTO userLoginDTO, boolean needAdmin);
}

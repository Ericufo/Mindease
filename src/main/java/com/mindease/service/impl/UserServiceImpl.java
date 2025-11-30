package com.mindease.service.impl;

import com.mindease.common.constant.MessageConstant;
import com.mindease.common.constant.StatusConstant;
import com.mindease.common.exception.AccountLockedException;
import com.mindease.common.exception.AccountNotFoundException;
import com.mindease.common.exception.PasswordErrorException;
import com.mindease.mapper.UserMapper;
import com.mindease.pojo.dto.UserLoginDTO;
import com.mindease.pojo.entity.User;
import com.mindease.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 登录
     *
     * @param userLoginDTO
     * @param needAdmin    是否需要管理员权限
     * @return
     */
    @Override
    public User login(UserLoginDTO userLoginDTO, boolean needAdmin) {
        String username = userLoginDTO.getUsername();
        String password = userLoginDTO.getPassword();

        User user = userMapper.getByUsername(username);
        if (user == null) {
            throw new AccountNotFoundException(MessageConstant.USER_NOT_FOUND);
        }

        // TODO: 全局异常处理器
        // 进行md5加密，然后再进行比对
        password = DigestUtils.md5DigestAsHex(password.getBytes());
        if (!password.equals(user.getPassword())) {
            //密码错误
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (user.getStatus() == StatusConstant.DISABLE) {
            //账号被锁定
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        // 防止用户使用管理端登录
        // TODO: 自定义权限不足异常类
        if (needAdmin) {
            if (!"ADMIN".equals(user.getRole()) && !"ROOT".equals(user.getRole())) {
                throw new RuntimeException("权限不足，非管理员禁止访问");
            }
        }

        return user;
    }
}

package com.mindease.controller.admin;

import com.mindease.common.result.Result;
import com.mindease.pojo.dto.UserLoginDTO;
import com.mindease.pojo.entity.User;
import com.mindease.pojo.vo.UserLoginVO;
import com.mindease.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *  超级管理员管理员工
 */
@RestController
@RequestMapping("/admin/common")
@Slf4j
public class CommonController {

    @Autowired
    private UserService userService;


    /**
     * 登录
     *
     * @param userLoginDTO
     * @return
     */
    @PostMapping("/login")
    public Result<UserLoginVO> login(@RequestBody UserLoginDTO userLoginDTO) {
        log.info("管理端登录:{}", userLoginDTO);

        User user = userService.login(userLoginDTO, true);

        //TODO: 自定义jwt工具类，生成jwt令牌，定义jwt令牌校验的拦截器
        String token = "";
        UserLoginVO userLoginVO = UserLoginVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(user.getRole())
                .token(token)
                .build();

        return Result.success(userLoginVO);
    }
}

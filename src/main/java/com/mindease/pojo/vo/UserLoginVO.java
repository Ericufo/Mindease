package com.mindease.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class UserLoginVO {

    private Long id;

    private String username;

    private String token;

    private String role;
}

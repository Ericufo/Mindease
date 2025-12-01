package com.mindease.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuditSubmitDTO {

    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    @NotBlank(message = "资质证书URL不能为空")
    private String qualificationUrl;

    private String idCardUrl;
}


package com.cib.ai.test.model;

import jakarta.validation.constraints.NotBlank;

/** 管理端登录入参：用户名 + 密码（与 C 端身份严格隔离） */
public record AdminLoginRequest(
        @NotBlank String username,
        @NotBlank String password) {
}

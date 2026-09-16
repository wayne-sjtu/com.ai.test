package com.ai.test.model;

import jakarta.validation.constraints.NotBlank;

/** C 端登录入参：手机号 + 密码 + 渠道码（渠道数据驱动识别） */
public record CustomerLoginRequest(
        @NotBlank String mobile,
        @NotBlank String password,
        @NotBlank String channelCode) {
}

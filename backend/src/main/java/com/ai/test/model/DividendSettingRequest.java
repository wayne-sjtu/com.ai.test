package com.ai.test.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 分红方式设置请求（spec 用户故事 23） */
public record DividendSettingRequest(
        @NotBlank(message = "产品代码不能为空")
        String productCode,

        @NotNull(message = "分红方式不能为空")
        @Pattern(regexp = "CASH|REINVEST", message = "分红方式仅支持 CASH/REINVEST")
        String dividendType) {
}

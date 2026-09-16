package com.cib.ai.test.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 渠道维护请求（状态/核心标记，spec 功能 2） */
public record ChannelUpdateRequest(
        /** ACTIVE / SUSPENDED */
        @Pattern(regexp = "ACTIVE|SUSPENDED", message = "状态仅允许 ACTIVE 或 SUSPENDED")
        String status,
        /** 核心渠道标识（可选更新） */
        Boolean coreFlag) {

    public boolean isEmpty() {
        return status == null && coreFlag == null;
    }
}

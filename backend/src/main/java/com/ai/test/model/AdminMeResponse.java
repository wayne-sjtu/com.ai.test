package com.ai.test.model;

import com.ai.test.repository.entity.Operator;

/** 管理端会话信息（登录响应与 /me 共用；不暴露 passwordHash） */
public record AdminMeResponse(
        String username,
        String displayName,
        String role) {

    public static AdminMeResponse from(Operator operator) {
        return new AdminMeResponse(
                operator.getUsername(),
                operator.getDisplayName(),
                operator.getRole());
    }
}

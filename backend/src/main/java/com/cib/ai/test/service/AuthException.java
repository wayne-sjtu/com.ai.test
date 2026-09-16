package com.cib.ai.test.service;

/**
 * 认证域业务异常。code 由适配层映射为 HTTP 状态：
 * AUTH_FAILED → 401；CHANNEL_INVALID / CHANNEL_SUSPENDED → 403。
 */
public class AuthException extends RuntimeException {

    private final String code;

    public AuthException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 登录失败统一话术，避免区分"用户不存在/密码错误"（防账号探测） */
    public static AuthException badCredentials(String message) {
        return new AuthException("AUTH_FAILED", message);
    }
}

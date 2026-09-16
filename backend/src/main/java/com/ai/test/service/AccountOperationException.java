package com.ai.test.service;

import java.util.List;

/**
 * 账户域业务异常。code → HTTP 映射见 ApiExceptionHandler：
 * SIGN_PRECONDITION_FAILED → 400；TERMINATE_REJECTED → 409（附原因列表）。
 */
public class AccountOperationException extends RuntimeException {

    private final String code;
    private final List<String> reasons;

    public AccountOperationException(String code, String message) {
        this(code, message, List.of());
    }

    public AccountOperationException(String code, String message, List<String> reasons) {
        super(message);
        this.code = code;
        this.reasons = reasons;
    }

    public String getCode() {
        return code;
    }

    public List<String> getReasons() {
        return reasons;
    }
}

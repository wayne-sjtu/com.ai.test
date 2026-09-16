package com.ai.test.service;

/**
 * 交易域业务异常。code → HTTP 映射见 ApiExceptionHandler：
 * ORDER_NOT_FOUND → 404；其余校验类 → 400。
 */
public class TradeException extends RuntimeException {

    private final String code;

    public TradeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

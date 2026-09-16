package com.ai.test.config;

/**
 * HttpSession 属性键与身份类型常量（ADR-0004）。
 * Controller 写入、AuthInterceptor 校验，两端共用。
 */
public final class SessionKeys {

    /** 身份类型：CUSTOMER / OPERATOR */
    public static final String AUTH_TYPE = "authType";
    public static final String CUSTOMER_NO = "customerNo";
    public static final String OPERATOR_USERNAME = "operatorUsername";

    public static final String TYPE_CUSTOMER = "CUSTOMER";
    public static final String TYPE_OPERATOR = "OPERATOR";

    private SessionKeys() {
    }
}

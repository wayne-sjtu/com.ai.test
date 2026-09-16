package com.ai.test.service;

import com.ai.test.repository.entity.Order;

/**
 * 交易下单结果：失败时不抛异常回滚（保证 FAILED 订单与轨迹留痕、资金/额度回补入库），
 * 由 Controller 映射为 400 + {code, message, orderNo}。
 */
public record PurchaseOutcome(Order order, String errorCode, String errorMessage) {

    public boolean failed() {
        return errorCode != null;
    }

    public static PurchaseOutcome success(Order order) {
        return new PurchaseOutcome(order, null, null);
    }

    public static PurchaseOutcome failure(Order order, String code, String message) {
        return new PurchaseOutcome(order, code, message);
    }
}

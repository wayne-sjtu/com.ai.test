package com.ai.test.model;

import com.ai.test.repository.entity.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 管理端订单视图：比 C 端多客户号（统一订单查询，spec 用户故事 32） */
public record AdminOrderResponse(
        String orderNo,
        String clientRequestId,
        String customerNo,
        String productCode,
        String productType,
        String orderType,
        BigDecimal amount,
        BigDecimal shares,
        BigDecimal fee,
        String status,
        String taSerialNo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AdminOrderResponse from(Order order) {
        return new AdminOrderResponse(
                order.getOrderNo(),
                order.getClientRequestId(),
                order.getCustomerNo(),
                order.getProductCode(),
                order.getProductType(),
                order.getOrderType(),
                order.getAmount(),
                order.getShares(),
                order.getFee(),
                order.getStatus(),
                order.getTaSerialNo(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}

package com.cib.ai.test.model;

import com.cib.ai.test.repository.entity.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单视图（C 端与后续管理端复用） */
public record OrderResponse(
        String orderNo,
        String productCode,
        String productType,
        String orderType,
        BigDecimal amount,
        BigDecimal shares,
        BigDecimal fee,
        String status,
        Boolean confirmRisk,
        String taSerialNo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getOrderNo(),
                order.getProductCode(),
                order.getProductType(),
                order.getOrderType(),
                order.getAmount(),
                order.getShares(),
                order.getFee(),
                order.getStatus(),
                order.getConfirmRisk(),
                order.getTaSerialNo(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}

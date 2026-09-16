package com.ai.test.model;

import com.ai.test.repository.entity.OrderEvent;
import java.time.LocalDateTime;

/** 订单事件轨迹视图（append-only，含 TA 标识与报文流水号，spec 用户故事 32） */
public record OrderEventResponse(
        Long id,
        String orderNo,
        String eventType,
        String fromStatus,
        String toStatus,
        String taTag,
        String taSerialNo,
        String detail,
        LocalDateTime createdAt) {

    public static OrderEventResponse from(OrderEvent event) {
        return new OrderEventResponse(
                event.getId(),
                event.getOrderNo(),
                event.getEventType(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getTaTag(),
                event.getTaSerialNo(),
                event.getDetail(),
                event.getCreatedAt());
    }
}

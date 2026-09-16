package com.ai.test.model;

import com.ai.test.repository.entity.Ticket;
import java.time.LocalDateTime;

/** 工单视图（C 端与管理端复用） */
public record TicketResponse(
        String ticketNo,
        String customerNo,
        String ticketType,
        String productCode,
        String productType,
        String content,
        String status,
        String externalSyncStatus,
        String handler,
        String reply,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static TicketResponse from(Ticket ticket, String productType) {
        return new TicketResponse(
                ticket.getTicketNo(),
                ticket.getCustomerNo(),
                ticket.getTicketType(),
                ticket.getProductCode(),
                productType,
                ticket.getContent(),
                ticket.getStatus(),
                ticket.getExternalSyncStatus(),
                ticket.getHandler(),
                ticket.getReply(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }
}

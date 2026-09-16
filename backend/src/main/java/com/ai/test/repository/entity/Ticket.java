package com.ai.test.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 客服工单（咨询/投诉；代销投诉同步发行机构闭环） */
@Entity
@Table(name = "ticket")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_no", nullable = false, unique = true, length = 32)
    private String ticketNo;

    @Column(name = "customer_no", nullable = false, length = 32)
    private String customerNo;

    /** CONSULT 咨询 / COMPLAINT 投诉 */
    @Column(name = "ticket_type", nullable = false, length = 20)
    private String ticketType;

    @Column(name = "product_code", length = 32)
    private String productCode;

    @Column(nullable = false, length = 1000)
    private String content;

    /** OPEN / PROCESSING / CLOSED */
    @Column(nullable = false, length = 20)
    private String status;

    /** 代销投诉外部同步状态：PENDING / SYNCED（自营工单为 NULL） */
    @Column(name = "external_sync_status", length = 20)
    private String externalSyncStatus;

    @Column(length = 50)
    private String handler;

    @Column(length = 1000)
    private String reply;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getTicketNo() { return ticketNo; }
    public void setTicketNo(String ticketNo) { this.ticketNo = ticketNo; }
    public String getCustomerNo() { return customerNo; }
    public void setCustomerNo(String customerNo) { this.customerNo = customerNo; }
    public String getTicketType() { return ticketType; }
    public void setTicketType(String ticketType) { this.ticketType = ticketType; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getExternalSyncStatus() { return externalSyncStatus; }
    public void setExternalSyncStatus(String externalSyncStatus) { this.externalSyncStatus = externalSyncStatus; }
    public String getHandler() { return handler; }
    public void setHandler(String handler) { this.handler = handler; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

package com.cib.ai.test.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 订单事件轨迹（append-only：每次状态迁移插一条，含 TA 标识与报文流水号，支撑追溯） */
@Entity
@Table(name = "order_event")
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 32)
    private String orderNo;

    /** CREATE / SUBMIT / TA_ACCEPT / CONFIRM / SETTLE / REDEEM / CANCEL / FAIL / PENDING_QUEUE */
    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", length = 20)
    private String toStatus;

    /** INTA / EXTA */
    @Column(name = "ta_tag", length = 10)
    private String taTag;

    @Column(name = "ta_serial_no", length = 40)
    private String taSerialNo;

    @Column(length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public String getTaTag() { return taTag; }
    public void setTaTag(String taTag) { this.taTag = taTag; }
    public String getTaSerialNo() { return taSerialNo; }
    public void setTaSerialNo(String taSerialNo) { this.taSerialNo = taSerialNo; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

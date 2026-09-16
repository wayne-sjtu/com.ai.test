package com.cib.ai.test.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单（单一订单表 + product_type 区分自营/代销，状态机见设计文档 5 章）。
 * 幂等：client_request_id 唯一（前端生成，重复提交返回原订单）。
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 32)
    private String orderNo;

    @Column(name = "client_request_id", nullable = false, unique = true, length = 64)
    private String clientRequestId;

    @Column(name = "customer_no", nullable = false, length = 32)
    private String customerNo;

    @Column(name = "product_code", nullable = false, length = 32)
    private String productCode;

    /** PROPRIETARY / CONSIGNMENT */
    @Column(name = "product_type", nullable = false, length = 20)
    private String productType;

    /** PURCHASE / REDEEM */
    @Column(name = "order_type", nullable = false, length = 20)
    private String orderType;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(precision = 18, scale = 2)
    private BigDecimal shares;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal fee;

    /**
     * CREATED / SUBMITTED / TA_ACCEPTED / CONFIRMED / SETTLED / REDEEMED /
     * CANCELLED / FAILED / EXPIRED / PENDING_QUEUE / REVERSED(预留)
     */
    @Column(nullable = false, length = 20)
    private String status;

    /** 适当性二次确认标志（CONFIRM 档经确认后重提交置 1） */
    @Column(name = "confirm_risk", nullable = false)
    private Boolean confirmRisk;

    @Column(name = "ta_serial_no", length = 40)
    private String taSerialNo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getClientRequestId() { return clientRequestId; }
    public void setClientRequestId(String clientRequestId) { this.clientRequestId = clientRequestId; }
    public String getCustomerNo() { return customerNo; }
    public void setCustomerNo(String customerNo) { this.customerNo = customerNo; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }
    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getShares() { return shares; }
    public void setShares(BigDecimal shares) { this.shares = shares; }
    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getConfirmRisk() { return confirmRisk; }
    public void setConfirmRisk(Boolean confirmRisk) { this.confirmRisk = confirmRisk; }
    public String getTaSerialNo() { return taSerialNo; }
    public void setTaSerialNo(String taSerialNo) { this.taSerialNo = taSerialNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

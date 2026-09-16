package com.cib.ai.test.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 适当性校验留痕（每次校验：PASS / CONFIRM / BLOCK + 上下文，管理端可查） */
@Entity
@Table(name = "suitability_log")
public class SuitabilityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_no", nullable = false, length = 32)
    private String customerNo;

    @Column(name = "product_code", nullable = false, length = 32)
    private String productCode;

    @Column(name = "customer_risk_level", nullable = false, length = 10)
    private String customerRiskLevel;

    @Column(name = "product_risk_level", nullable = false, length = 10)
    private String productRiskLevel;

    /** PASS / CONFIRM / BLOCK */
    @Column(nullable = false, length = 20)
    private String result;

    /** CONFIRM 档经客户二次确认后置 1 */
    @Column(nullable = false)
    private Boolean confirmed;

    @Column(name = "order_no", length = 32)
    private String orderNo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public String getCustomerNo() { return customerNo; }
    public void setCustomerNo(String customerNo) { this.customerNo = customerNo; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getCustomerRiskLevel() { return customerRiskLevel; }
    public void setCustomerRiskLevel(String customerRiskLevel) { this.customerRiskLevel = customerRiskLevel; }
    public String getProductRiskLevel() { return productRiskLevel; }
    public void setProductRiskLevel(String productRiskLevel) { this.productRiskLevel = productRiskLevel; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Boolean getConfirmed() { return confirmed; }
    public void setConfirmed(Boolean confirmed) { this.confirmed = confirmed; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

package com.cib.ai.test.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 分红方式设置（CASH 现金分红 / REINVEST 红利再投资） */
@Entity
@Table(name = "dividend_setting")
public class DividendSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_no", nullable = false, length = 32)
    private String customerNo;

    @Column(name = "product_code", nullable = false, length = 32)
    private String productCode;

    /** CASH / REINVEST */
    @Column(name = "dividend_type", nullable = false, length = 20)
    private String dividendType;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getCustomerNo() { return customerNo; }
    public void setCustomerNo(String customerNo) { this.customerNo = customerNo; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getDividendType() { return dividendType; }
    public void setDividendType(String dividendType) { this.dividendType = dividendType; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

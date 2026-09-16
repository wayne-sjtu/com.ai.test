package com.ai.test.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 投资计划（预约申购 RESERVE / 定投 REGULAR_INVEST，@Scheduled 扫描到期触发下单） */
@Entity
@Table(name = "invest_plan")
public class InvestPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_no", nullable = false, unique = true, length = 32)
    private String planNo;

    @Column(name = "customer_no", nullable = false, length = 32)
    private String customerNo;

    @Column(name = "product_code", nullable = false, length = 32)
    private String productCode;

    /** RESERVE 预约申购 / REGULAR_INVEST 定投 */
    @Column(name = "plan_type", nullable = false, length = 20)
    private String planType;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /** 预约申购触发日 */
    @Column(name = "trigger_date")
    private LocalDate triggerDate;

    /** 定投周期：DAY / WEEK / MONTH */
    @Column(name = "period_type", length = 10)
    private String periodType;

    @Column(name = "next_trigger_date")
    private LocalDate nextTriggerDate;

    /** ACTIVE / FINISHED / CANCELLED / EXPIRED */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public String getPlanNo() { return planNo; }
    public void setPlanNo(String planNo) { this.planNo = planNo; }
    public String getCustomerNo() { return customerNo; }
    public void setCustomerNo(String customerNo) { this.customerNo = customerNo; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getPlanType() { return planType; }
    public void setPlanType(String planType) { this.planType = planType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getTriggerDate() { return triggerDate; }
    public void setTriggerDate(LocalDate triggerDate) { this.triggerDate = triggerDate; }
    public String getPeriodType() { return periodType; }
    public void setPeriodType(String periodType) { this.periodType = periodType; }
    public LocalDate getNextTriggerDate() { return nextTriggerDate; }
    public void setNextTriggerDate(LocalDate nextTriggerDate) { this.nextTriggerDate = nextTriggerDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

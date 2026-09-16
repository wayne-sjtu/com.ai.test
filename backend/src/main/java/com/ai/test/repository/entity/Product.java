package com.ai.test.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 理财产品（自营 PROPRIETARY / 代销 CONSIGNMENT，统一货架单表隔离标识） */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_code", nullable = false, unique = true, length = 32)
    private String productCode;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    /** PROPRIETARY 自营 / CONSIGNMENT 代销 */
    @Column(name = "product_type", nullable = false, length = 20)
    private String productType;

    @Column(name = "issuer_name", nullable = false, length = 100)
    private String issuerName;

    /** R1 ~ R5 */
    @Column(name = "risk_level", nullable = false, length = 10)
    private String riskLevel;

    /** 品类：货币 / 固收 / 混合 / 股票 等 */
    @Column(nullable = false, length = 30)
    private String category;

    @Column(name = "term_days", nullable = false)
    private Integer termDays;

    @Column(name = "min_purchase_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal minPurchaseAmount;

    @Column(name = "purchase_fee_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal purchaseFeeRate;

    @Column(name = "redemption_fee_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal redemptionFeeRate;

    /** 巨额赎回阈值（单日赎回份额超产品总份额该比例时延期确认，默认 10%） */
    @Column(name = "redeem_threshold_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal redeemThresholdRate;

    @Column(name = "expected_return", length = 50)
    private String expectedReturn;

    /** 产品总额度（在途订单占用，行锁控制并发） */
    @Column(name = "total_quota", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalQuota;

    @Column(name = "used_quota", nullable = false, precision = 18, scale = 2)
    private BigDecimal usedQuota;

    /** 交易时段（产品级配置） */
    @Column(name = "trade_start_time", nullable = false)
    private LocalTime tradeStartTime;

    @Column(name = "trade_end_time", nullable = false)
    private LocalTime tradeEndTime;

    /** OPEN / SUSPENDED / CLOSED */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public String getIssuerName() {
        return issuerName;
    }

    public void setIssuerName(String issuerName) {
        this.issuerName = issuerName;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getTermDays() {
        return termDays;
    }

    public void setTermDays(Integer termDays) {
        this.termDays = termDays;
    }

    public BigDecimal getMinPurchaseAmount() {
        return minPurchaseAmount;
    }

    public void setMinPurchaseAmount(BigDecimal minPurchaseAmount) {
        this.minPurchaseAmount = minPurchaseAmount;
    }

    public BigDecimal getPurchaseFeeRate() {
        return purchaseFeeRate;
    }

    public void setPurchaseFeeRate(BigDecimal purchaseFeeRate) {
        this.purchaseFeeRate = purchaseFeeRate;
    }

    public BigDecimal getRedemptionFeeRate() {
        return redemptionFeeRate;
    }

    public void setRedemptionFeeRate(BigDecimal redemptionFeeRate) {
        this.redemptionFeeRate = redemptionFeeRate;
    }

    public BigDecimal getRedeemThresholdRate() {
        return redeemThresholdRate;
    }

    public void setRedeemThresholdRate(BigDecimal redeemThresholdRate) {
        this.redeemThresholdRate = redeemThresholdRate;
    }

    public String getExpectedReturn() {
        return expectedReturn;
    }

    public void setExpectedReturn(String expectedReturn) {
        this.expectedReturn = expectedReturn;
    }

    public BigDecimal getTotalQuota() {
        return totalQuota;
    }

    public void setTotalQuota(BigDecimal totalQuota) {
        this.totalQuota = totalQuota;
    }

    public BigDecimal getUsedQuota() {
        return usedQuota;
    }

    public void setUsedQuota(BigDecimal usedQuota) {
        this.usedQuota = usedQuota;
    }

    public LocalTime getTradeStartTime() {
        return tradeStartTime;
    }

    public void setTradeStartTime(LocalTime tradeStartTime) {
        this.tradeStartTime = tradeStartTime;
    }

    public LocalTime getTradeEndTime() {
        return tradeEndTime;
    }

    public void setTradeEndTime(LocalTime tradeEndTime) {
        this.tradeEndTime = tradeEndTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

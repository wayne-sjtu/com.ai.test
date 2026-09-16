package com.cib.ai.test.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 适当性 C×R 动作映射规则（数据驱动，设计文档第 7 章：PASS/CONFIRM/BLOCK） */
@Entity
@Table(name = "suitability_rule")
public class SuitabilityRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 客户风险等级 C1 ~ C5 */
    @Column(name = "customer_level", nullable = false, length = 10)
    private String customerLevel;

    /** 产品风险等级 R1 ~ R5 */
    @Column(name = "product_level", nullable = false, length = 10)
    private String productLevel;

    /** PASS 通过 / CONFIRM 二次确认 / BLOCK 拦截 */
    @Column(nullable = false, length = 20)
    private String action;

    public Long getId() { return id; }
    public String getCustomerLevel() { return customerLevel; }
    public void setCustomerLevel(String customerLevel) { this.customerLevel = customerLevel; }
    public String getProductLevel() { return productLevel; }
    public void setProductLevel(String productLevel) { this.productLevel = productLevel; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}

package com.ai.test.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 理财账户（签约状态机：UNSIGNED → SIGNED → TERMINATED，ADR 见 spec Q8） */
@Entity
@Table(name = "wealth_account")
public class WealthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_no", nullable = false, unique = true, length = 32)
    private String accountNo;

    @Column(name = "customer_no", nullable = false, length = 32)
    private String customerNo;

    /** UNSIGNED / SIGNED / TERMINATED */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "ta_account_no", length = 32)
    private String taAccountNo;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public String getAccountNo() { return accountNo; }
    public void setAccountNo(String accountNo) { this.accountNo = accountNo; }
    public String getCustomerNo() { return customerNo; }
    public void setCustomerNo(String customerNo) { this.customerNo = customerNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTaAccountNo() { return taAccountNo; }
    public void setTaAccountNo(String taAccountNo) { this.taAccountNo = taAccountNo; }
    public LocalDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }
    public LocalDateTime getTerminatedAt() { return terminatedAt; }
    public void setTerminatedAt(LocalDateTime terminatedAt) { this.terminatedAt = terminatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

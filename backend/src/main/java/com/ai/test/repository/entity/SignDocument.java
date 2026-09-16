package com.ai.test.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 电子签署文件（适当性档案：权益须知/风险告知/产品协议/代销揭示） */
@Entity
@Table(name = "sign_document")
public class SignDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_no", nullable = false, length = 32)
    private String customerNo;

    /** RIGHTS_NOTICE / RISK_DISCLOSURE / PRODUCT_AGREEMENT / CONSIGNMENT_DISCLOSURE */
    @Column(name = "doc_type", nullable = false, length = 50)
    private String docType;

    /** 关联产品码（非产品级文件时为 '-'） */
    @Column(name = "product_code", nullable = false, length = 32)
    private String productCode;

    @Column(name = "signed_at", nullable = false)
    private LocalDateTime signedAt;

    public Long getId() { return id; }
    public String getCustomerNo() { return customerNo; }
    public void setCustomerNo(String customerNo) { this.customerNo = customerNo; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public LocalDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }
}

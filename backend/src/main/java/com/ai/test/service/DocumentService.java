package com.ai.test.service;

import com.ai.test.model.SignedDocumentsResponse;
import com.ai.test.repository.SignDocumentRepository;
import com.ai.test.repository.entity.SignDocument;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 电子签署用例（spec 功能 6）：四类适当性档案文件签收留痕，重复签署幂等 */
@Service
public class DocumentService {

    private static final Set<String> VALID_DOC_TYPES = Set.of(
            "RIGHTS_NOTICE", "RISK_DISCLOSURE", "PRODUCT_AGREEMENT", "CONSIGNMENT_DISCLOSURE");

    private final SignDocumentRepository signDocumentRepository;

    public DocumentService(SignDocumentRepository signDocumentRepository) {
        this.signDocumentRepository = signDocumentRepository;
    }

    /** 签收文件：同一文件重复签收幂等（不重复插入），productCode 缺省为 '-' */
    @Transactional
    public SignedDocumentsResponse sign(String customerNo, String docType, String productCode) {
        if (!VALID_DOC_TYPES.contains(docType)) {
            throw new IllegalArgumentException("不支持的文件类型：" + docType);
        }
        String code = (productCode == null || productCode.isBlank()) ? "-" : productCode;
        boolean alreadySigned = signDocumentRepository
                .findByCustomerNo(customerNo).stream()
                .anyMatch(d -> d.getDocType().equals(docType) && d.getProductCode().equals(code));
        if (!alreadySigned) {
            SignDocument document = new SignDocument();
            document.setCustomerNo(customerNo);
            document.setDocType(docType);
            document.setProductCode(code);
            document.setSignedAt(LocalDateTime.now());
            signDocumentRepository.save(document);
        }
        return list(customerNo);
    }

    /** 已签署文件列表 */
    public SignedDocumentsResponse list(String customerNo) {
        return SignedDocumentsResponse.from(signDocumentRepository.findByCustomerNo(customerNo));
    }
}

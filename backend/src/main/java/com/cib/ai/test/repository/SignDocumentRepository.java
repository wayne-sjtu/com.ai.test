package com.cib.ai.test.repository;

import com.cib.ai.test.repository.entity.SignDocument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 电子签署文件数据访问（适当性档案留痕） */
public interface SignDocumentRepository extends JpaRepository<SignDocument, Long> {

    List<SignDocument> findByCustomerNo(String customerNo);

    long countByCustomerNoAndDocTypeAndProductCode(String customerNo, String docType, String productCode);
}

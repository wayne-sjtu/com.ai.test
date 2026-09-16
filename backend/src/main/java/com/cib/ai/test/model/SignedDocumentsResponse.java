package com.cib.ai.test.model;

import com.cib.ai.test.repository.entity.SignDocument;
import java.time.LocalDateTime;
import java.util.List;

/** 已签署文件列表（适当性档案） */
public record SignedDocumentsResponse(List<Item> documents) {

    public static SignedDocumentsResponse from(List<SignDocument> documents) {
        return new SignedDocumentsResponse(documents.stream()
                .map(d -> new Item(d.getDocType(), d.getProductCode(), d.getSignedAt()))
                .toList());
    }

    public record Item(String docType, String productCode, LocalDateTime signedAt) {
    }
}

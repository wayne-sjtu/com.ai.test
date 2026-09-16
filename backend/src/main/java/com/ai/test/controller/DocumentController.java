package com.ai.test.controller;

import com.ai.test.model.SignedDocumentsResponse;
import com.ai.test.service.DocumentService;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** C 端电子签署端点（spec 功能 6）：四类适当性档案文件签收 */
@RestController
@RequestMapping("/api/customer/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/{type}/sign")
    public SignedDocumentsResponse sign(@PathVariable("type") String type,
                                        @RequestBody(required = false) Map<String, String> body,
                                        HttpSession session) {
        return documentService.sign(
                CustomerAccountController.customerNo(session),
                type,
                body == null ? null : body.get("productCode"));
    }

    @GetMapping
    public SignedDocumentsResponse list(HttpSession session) {
        return documentService.list(CustomerAccountController.customerNo(session));
    }
}

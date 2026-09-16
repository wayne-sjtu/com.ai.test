package com.ai.test.controller;

import com.ai.test.repository.DualRecordRepository;
import com.ai.test.repository.entity.DualRecord;
import com.ai.test.service.TradeException;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端双录端点（spec 功能 11）：代销 R4/R5 交易前置。
 * Phase 1 本体 Mock 为标志文件上传（直接置 COMPLETED），未完成则交易校验链拦截。
 */
@RestController
@RequestMapping("/api/customer/dual-records")
public class DualRecordController {

    private final DualRecordRepository dualRecordRepository;

    public DualRecordController(DualRecordRepository dualRecordRepository) {
        this.dualRecordRepository = dualRecordRepository;
    }

    /** Mock 双录上传：创建 COMPLETED 标志（重复上传幂等） */
    @PostMapping("/mock-upload")
    public Map<String, Object> mockUpload(@RequestBody Map<String, String> body, HttpSession session) {
        String customerNo = CustomerAccountController.customerNo(session);
        String productCode = body.get("productCode");
        if (productCode == null || productCode.isBlank()) {
            throw new TradeException("PARAM_INVALID", "productCode 不能为空");
        }
        boolean already = dualRecordRepository
                .findFirstByCustomerNoAndProductCodeAndStatusOrderByIdDesc(
                        customerNo, productCode, "COMPLETED")
                .isPresent();
        if (!already) {
            DualRecord record = new DualRecord();
            record.setOrderNo("MOCK-" + System.currentTimeMillis());
            record.setCustomerNo(customerNo);
            record.setProductCode(productCode);
            record.setFileFlag("dual-record-" + customerNo + "-" + productCode + ".mp4");
            record.setStatus("COMPLETED");
            record.setCreatedAt(LocalDateTime.now());
            dualRecordRepository.save(record);
        }
        return Map.of("productCode", productCode, "status", "COMPLETED");
    }
}

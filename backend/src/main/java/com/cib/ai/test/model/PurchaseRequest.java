package com.cib.ai.test.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * 申购入参。clientRequestId 为前端生成的幂等键，重复提交返回原订单；
 * confirmRisk 用于适当性 CONFIRM 档二次确认后重提交。
 */
public record PurchaseRequest(
        @NotBlank String productCode,
        @NotNull @Positive BigDecimal amount,
        boolean confirmRisk,
        @NotBlank String clientRequestId) {
}

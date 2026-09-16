package com.cib.ai.test.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** 赎回入参（按份额赎回，回款金额 = 份额 × 最新净值 − 赎回费） */
public record RedeemRequest(
        @NotBlank String productCode,
        @NotNull @Positive BigDecimal shares,
        @NotBlank String clientRequestId) {
}

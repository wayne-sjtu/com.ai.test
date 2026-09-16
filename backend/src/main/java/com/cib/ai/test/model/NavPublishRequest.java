package com.cib.ai.test.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 自营净值发布入参（管理端运营，spec 用户故事 33） */
public record NavPublishRequest(
        @NotBlank String productCode,
        @NotNull @PastOrPresent LocalDate navDate,
        @NotNull @Positive BigDecimal nav) {
}

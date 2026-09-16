package com.cib.ai.test.model;

import com.cib.ai.test.repository.entity.InvestPlan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 投资计划视图（C 端列表展示） */
public record InvestPlanResponse(
        String planNo,
        String productCode,
        String planType,
        BigDecimal amount,
        LocalDate triggerDate,
        String periodType,
        LocalDate nextTriggerDate,
        String status,
        LocalDateTime createdAt) {

    public static InvestPlanResponse from(InvestPlan plan) {
        return new InvestPlanResponse(
                plan.getPlanNo(),
                plan.getProductCode(),
                plan.getPlanType(),
                plan.getAmount(),
                plan.getTriggerDate(),
                plan.getPeriodType(),
                plan.getNextTriggerDate(),
                plan.getStatus(),
                plan.getCreatedAt());
    }
}

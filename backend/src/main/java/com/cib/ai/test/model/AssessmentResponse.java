package com.cib.ai.test.model;

import com.cib.ai.test.repository.entity.RiskAssessment;
import java.time.LocalDateTime;

/** 测评结果：等级、分数、有效期；expired 供交易前置校验强制重测引导 */
public record AssessmentResponse(
        String riskLevel,
        int score,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        boolean expired) {

    public static AssessmentResponse from(RiskAssessment assessment, boolean expired) {
        return new AssessmentResponse(
                assessment.getRiskLevel(),
                assessment.getScore(),
                assessment.getValidFrom(),
                assessment.getValidTo(),
                expired);
    }
}

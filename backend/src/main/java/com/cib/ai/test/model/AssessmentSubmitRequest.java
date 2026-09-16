package com.cib.ai.test.model;

import jakarta.validation.constraints.NotBlank;

/** 测评提交入参：5 题答案快照，如 "A,B,C,D,A" */
public record AssessmentSubmitRequest(@NotBlank String answers) {
}

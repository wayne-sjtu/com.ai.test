package com.ai.test.domain;

import java.util.Arrays;
import java.util.List;

/**
 * 风险测评评分与 C1-C5 等级映射（纯领域规则，无框架依赖）。
 * 问卷固定 5 题，每题选项 A/B/C/D 对应 5/10/15/20 分，满分 100；
 * 等级映射与种子数据分数一致：35→C1、45→C2、60→C3、70→C4、82→C5。
 */
public final class RiskLevelRule {

    public static final int QUESTION_COUNT = 5;
    private static final List<String> VALID_OPTIONS = List.of("A", "B", "C", "D");

    private RiskLevelRule() {
    }

    /** 校验答案格式："A,B,C,D,A"，5 题、每题 A-D */
    public static void validate(String answers) {
        List<String> parts = split(answers);
        if (parts.size() != QUESTION_COUNT || parts.stream().anyMatch(p -> !VALID_OPTIONS.contains(p))) {
            throw new IllegalArgumentException(
                    "答案格式不合法：须为 " + QUESTION_COUNT + " 题且每题为 A/B/C/D，如 \"A,B,C,D,A\"");
        }
    }

    /** 计算总分：A=5 / B=10 / C=15 / D=20 */
    public static int score(String answers) {
        validate(answers);
        return split(answers).stream()
                .mapToInt(option -> (VALID_OPTIONS.indexOf(option) + 1) * 5)
                .sum();
    }

    /** 分数 → C 等级（映射边界与种子演示分数一致） */
    public static String level(int score) {
        if (score < 40) return "C1";
        if (score < 60) return "C2";
        if (score < 70) return "C3";
        if (score < 80) return "C4";
        return "C5";
    }

    private static List<String> split(String answers) {
        if (answers == null || answers.isBlank()) {
            return List.of();
        }
        return Arrays.stream(answers.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .toList();
    }
}

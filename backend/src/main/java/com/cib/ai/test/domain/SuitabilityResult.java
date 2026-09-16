package com.cib.ai.test.domain;

/**
 * 适当性校验结果（纯领域值对象，无框架依赖）。
 * 交易校验链依据此结果决定继续 / 要求二次确认 / 终止。
 */
public record SuitabilityResult(
        /** 引擎动作：PASS / CONFIRM / BLOCK */
        String action,
        /** 是否放行（PASS，或 CONFIRM 且客户已二次确认） */
        boolean passed,
        /** 是否需要前端二次确认（CONFIRM 且尚未确认） */
        boolean needConfirm,
        /** 提示文案（拦截原因 / 风险提示 / 引导重测） */
        String message) {

    public static SuitabilityResult pass() {
        return new SuitabilityResult("PASS", true, false, null);
    }

    public static SuitabilityResult needConfirm(String hint) {
        return new SuitabilityResult("CONFIRM", false, true, hint);
    }

    public static SuitabilityResult confirmed(String hint) {
        return new SuitabilityResult("CONFIRM", true, false, hint);
    }

    public static SuitabilityResult block(String reason) {
        return new SuitabilityResult("BLOCK", false, false, reason);
    }
}

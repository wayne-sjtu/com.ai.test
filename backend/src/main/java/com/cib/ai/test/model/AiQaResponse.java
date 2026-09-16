package com.cib.ai.test.model;

/**
 * AI 投教问答响应：强制带来源提示、风险声明与人工转接标记（spec 用户故事 30）。
 */
public record AiQaResponse(
        /** 回答内容 */
        String answer,
        /** 来源：LLM / FAQ / HUMAN / NONE */
        String source,
        /** 风险声明（所有回答强制附带） */
        String riskDisclaimer,
        /** 是否建议/已转人工 */
        boolean needHuman,
        /** 转人工自动创建的工单号（敏感问题时返回） */
        String ticketNo,
        /** FAQ 命中的标准问题（来源提示） */
        String faqQuestion) {
}

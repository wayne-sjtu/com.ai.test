package com.cib.ai.test.llm;

import java.util.Optional;

/**
 * LLM 抽象契约（AI 投教，spec 用户故事 30）：
 * Phase 1 进程内 Mock（主题模板回答，零外部依赖）；
 * Phase 2 可替换为真实 LLM API 实现，业务代码零改动。
 */
public interface LlmClient {

    /**
     * AI 投教问答：命中可答主题返回回答；
     * 无法回答或服务不可用返回 empty（触发 FAQ 降级）。
     */
    Optional<String> ask(String question);

    /** 服务是否可用（健康检查与降级演示用） */
    boolean available();
}

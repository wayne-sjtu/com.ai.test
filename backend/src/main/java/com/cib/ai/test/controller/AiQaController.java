package com.cib.ai.test.controller;

import com.cib.ai.test.model.AiQaRequest;
import com.cib.ai.test.model.AiQaResponse;
import com.cib.ai.test.service.AiQaService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端 AI 投教问答端点（spec 用户故事 30，设计文档 4.1 `/ai-qa`）：
 * LLM 优先 + FAQ 关键词降级；答案带来源提示、风险声明与人工转接标记。
 */
@RestController
@RequestMapping("/api/customer/ai-qa")
public class AiQaController {

    private final AiQaService aiQaService;

    public AiQaController(AiQaService aiQaService) {
        this.aiQaService = aiQaService;
    }

    @PostMapping
    public AiQaResponse ask(@Valid @RequestBody AiQaRequest request, HttpSession session) {
        return aiQaService.ask(CustomerAccountController.customerNo(session), request);
    }
}

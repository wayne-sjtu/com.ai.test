package com.ai.test.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** AI 投教问答请求（spec 用户故事 30） */
public record AiQaRequest(
        @NotBlank(message = "问题内容不能为空")
        @Size(max = 500, message = "问题长度不能超过 500")
        String question) {
}

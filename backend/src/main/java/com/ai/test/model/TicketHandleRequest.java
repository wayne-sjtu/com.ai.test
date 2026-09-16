package com.ai.test.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 管理端工单处理请求（spec 用户故事 31）：受理或关闭（关闭附答复） */
public record TicketHandleRequest(
        @NotBlank(message = "处理动作不能为空")
        @Pattern(regexp = "ACCEPT|CLOSE", message = "处理动作仅支持 ACCEPT/CLOSE")
        String action,

        /** 处理答复（CLOSE 关闭时必填，将推送客户站内消息） */
        @Size(max = 1000, message = "答复长度不能超过 1000")
        String reply) {
}

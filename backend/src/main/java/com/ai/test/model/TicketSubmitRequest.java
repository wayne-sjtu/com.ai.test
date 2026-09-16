package com.ai.test.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** C 端工单提交请求（spec 用户故事 29） */
public record TicketSubmitRequest(
        @NotBlank(message = "工单类型不能为空")
        @Pattern(regexp = "CONSULT|COMPLAINT", message = "工单类型仅支持 CONSULT/COMPLAINT")
        String ticketType,

        /** 关联产品代码（投诉代销产品时必填，用于外部同步路由） */
        @Size(max = 32, message = "产品代码长度不能超过 32")
        String productCode,

        @NotBlank(message = "工单内容不能为空")
        @Size(max = 1000, message = "工单内容长度不能超过 1000")
        String content) {
}

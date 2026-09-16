package com.ai.test.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 投资计划创建请求（spec 用户故事 22）：
 * RESERVE 预约申购（triggerDate 必填，到期一次性下单）；
 * REGULAR_INVEST 定投（periodType 必填，周期性自动申购）。
 */
public record InvestPlanCreateRequest(
        @NotBlank(message = "产品代码不能为空")
        String productCode,

        @NotBlank(message = "计划类型不能为空")
        @Pattern(regexp = "RESERVE|REGULAR_INVEST", message = "计划类型仅支持 RESERVE/REGULAR_INVEST")
        String planType,

        @NotNull(message = "申购金额不能为空")
        @Positive(message = "申购金额必须大于 0")
        BigDecimal amount,

        /** 首次触发日（RESERVE = 预约申购日；REGULAR_INVEST = 首期扣款日，不得早于今日） */
        @NotNull(message = "触发日期不能为空")
        LocalDate triggerDate,

        /** 定投周期：DAY / WEEK / MONTH（REGULAR_INVEST 必填） */
        @Pattern(regexp = "DAY|WEEK|MONTH", message = "定投周期仅支持 DAY/WEEK/MONTH")
        String periodType) {
}

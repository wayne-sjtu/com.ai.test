package com.cib.ai.test.domain;

import java.util.Map;
import java.util.Set;

/**
 * 订单状态机（设计文档 5 章，来自拷问共识）：
 * 申购: CREATED → SUBMITTED → TA_ACCEPTED → CONFIRMED → SETTLED
 * 赎回: CREATED → SUBMITTED → TA_ACCEPTED → CONFIRMED → REDEEMED
 * 异常终态: CANCELLED / FAILED / EXPIRED；特殊: PENDING_QUEUE → CONFIRMED
 * 约束: 状态只进不退；每次迁移写一条订单事件。
 */
public final class OrderStateMachine {

    public static final Set<String> TERMINAL_STATUSES = Set.of("SETTLED", "REDEEMED", "CANCELLED", "FAILED", "EXPIRED");

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "CREATED", Set.of("SUBMITTED", "FAILED", "CANCELLED"),
            "SUBMITTED", Set.of("TA_ACCEPTED", "FAILED", "CANCELLED"),
            // TA_ACCEPTED → CANCELLED：可撤时段 = TA 确认前（异步等待窗口），撤单与回调以状态机守卫互斥
            "TA_ACCEPTED", Set.of("CONFIRMED", "PENDING_QUEUE", "FAILED", "CANCELLED"),
            "PENDING_QUEUE", Set.of("CONFIRMED", "FAILED"),
            "CONFIRMED", Set.of("SETTLED", "REDEEMED"));

    private OrderStateMachine() {
    }

    /** 校验迁移合法性：非法迁移抛 IllegalStateException（应用层 bug，非用户错误） */
    public static void assertTransition(String from, String to) {
        Set<String> allowed = TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new IllegalStateException("非法状态迁移：" + from + " → " + to);
        }
    }

    /** 是否可撤单（TA 确认前的在途状态） */
    public static boolean cancellable(String status) {
        return "CREATED".equals(status) || "SUBMITTED".equals(status) || "TA_ACCEPTED".equals(status);
    }

    /** 是否在途（占用额度/资金/份额） */
    public static boolean inFlight(String status) {
        return !TERMINAL_STATUSES.contains(status);
    }
}

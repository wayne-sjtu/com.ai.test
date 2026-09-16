package com.ai.test.taclient;

/** 外部 TA 故障注入开关（设计文档 6.3 节） */
public enum FaultMode {
    /** 正常：受理后异步回调确认 */
    NORMAL,
    /** 通信超时：模拟外部 TA 无响应（Phase 2 HTTP Mock 为真实 30s 挂起） */
    TIMEOUT,
    /** 受理后拒绝 */
    REJECT,
    /** 延迟确认：回调显著延迟（Phase 2 为 60s，Phase 1 演示级 1s） */
    DELAY_CONFIRM
}

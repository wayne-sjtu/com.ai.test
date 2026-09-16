package com.ai.test.taclient;

/**
 * 外部 TA 异步确认回调的银行侧处理器。
 * local Profile 由 InProcessExternalTaClient 延迟触发；
 * compose Profile 由回调 HTTP 端点（TaCallbackController）调用。
 */
public interface TaCallbackHandler {

    void handle(TaCallback callback);
}

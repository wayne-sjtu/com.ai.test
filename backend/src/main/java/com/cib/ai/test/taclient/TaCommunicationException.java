package com.cib.ai.test.taclient;

/**
 * TA 通信异常（超时等）。
 * 调用方按重试策略处理：超时重试 3 次（指数退避），仍失败转人工工单（设计文档 6.5 节）。
 */
public class TaCommunicationException extends RuntimeException {

    public TaCommunicationException(String message) {
        super(message);
    }
}

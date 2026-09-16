package com.ai.test.taclient;

/**
 * TA 分红方式设置指令报文（功能 12：分红方式走 TA 报文真实实现）。
 *
 * @param customerNo   客户号
 * @param productCode  产品码
 * @param dividendType CASH 现金分红 / REINVEST 红利再投资
 */
public record DividendCommand(
        String customerNo,
        String productCode,
        String dividendType) {
}

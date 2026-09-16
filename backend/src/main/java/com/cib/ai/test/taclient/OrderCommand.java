package com.cib.ai.test.taclient;

import java.math.BigDecimal;

/**
 * TA 交易指令报文（申购/赎回共用，方向由调用方法区分）。
 *
 * @param orderNo     订单号
 * @param productCode 产品码
 * @param customerNo  客户号
 * @param amount      申购金额（申购指令必填）
 * @param shares      赎回份额（赎回指令必填）
 */
public record OrderCommand(
        String orderNo,
        String productCode,
        String customerNo,
        BigDecimal amount,
        BigDecimal shares) {
}

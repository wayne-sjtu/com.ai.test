package com.ai.test.taclient;

import java.math.BigDecimal;

/**
 * 外部 TA 异步确认回调报文。
 *
 * @param serialNo        回调报文流水号（幂等去重键）
 * @param orderNo         关联订单号
 * @param status          确认状态：CONFIRMED / REJECTED
 * @param confirmedShares 确认份额
 * @param message         说明信息
 */
public record TaCallback(
        String serialNo,
        String orderNo,
        String status,
        BigDecimal confirmedShares,
        String message) {
}

package com.cib.ai.test.taclient;

import java.math.BigDecimal;

/**
 * TA 应答报文。
 *
 * @param serialNo        报文流水号（{TA标识}-{yyMMdd}-{8位序列}）
 * @param status          受理/确认状态：ACCEPTED（外部TA已受理）/ CONFIRMED（本行TA同步确认）/ REJECTED（拒绝）
 * @param confirmedShares 确认份额（CONFIRMED 时非空）
 * @param message         说明信息
 */
public record TaResult(
        String serialNo,
        String status,
        BigDecimal confirmedShares,
        String message) {
}

package com.cib.ai.test.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 估值计算纯函数（设计文档 7 章估值口径，domain 层不依赖框架）：
 * 市值 = 份额 × 最新已确认净值；自营/代销同一估值函数，仅来源与时间戳标注不同。
 */
public final class ValuationCalculator {

    private ValuationCalculator() {
    }

    /** 持仓市值（份额 × 最新净值，两位小数） */
    public static BigDecimal marketValue(BigDecimal shares, BigDecimal latestNav) {
        if (shares == null || latestNav == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return shares.multiply(latestNav).setScale(2, RoundingMode.HALF_UP);
    }

    /** 浮动盈亏 = 市值 − 持仓成本 */
    public static BigDecimal floatingPnl(BigDecimal marketValue, BigDecimal costAmount) {
        return marketValue.subtract(costAmount == null ? BigDecimal.ZERO : costAmount)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** 收益率 = 浮动盈亏 / 成本（百分比，成本为零时返回 0） */
    public static BigDecimal returnRate(BigDecimal floatingPnl, BigDecimal costAmount) {
        if (costAmount == null || costAmount.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return floatingPnl.divide(costAmount, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}

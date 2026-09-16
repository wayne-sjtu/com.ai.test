package com.cib.ai.test.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一持仓视图（spec 用户故事 25/26）：
 * 自营/代销同视图清晰区分；市值/浮动盈亏实时计算（份额 × 最新净值）；
 * 代销净值带来源标注（EXTERNAL + syncedAt，数据以外部 TA 为准）；含在途资产。
 */
public record PositionViewResponse(
        List<PositionItem> positions,
        List<InFlightItem> inFlights,
        Summary summary) {

    /** 单笔持仓：估值口径自营/代销一致，仅来源标注不同 */
    public record PositionItem(
            String productCode,
            String productName,
            String productType,
            String riskLevel,
            String category,
            BigDecimal shares,
            BigDecimal frozenShares,
            BigDecimal availableShares,
            BigDecimal latestNav,
            String navSource,
            LocalDateTime navSyncedAt,
            BigDecimal costAmount,
            BigDecimal marketValue,
            BigDecimal floatingPnl,
            BigDecimal returnRatePercent) {
    }

    /** 在途资产（TA 确认前的申购金额/赎回份额） */
    public record InFlightItem(
            String orderNo,
            String productCode,
            String productName,
            String productType,
            String orderType,
            String status,
            BigDecimal amount,
            BigDecimal shares) {
    }

    /** 汇总：总口径 + 自营/代销分区小计（两类资产清晰区分） */
    public record Summary(
            BigDecimal totalMarketValue,
            BigDecimal totalCost,
            BigDecimal totalFloatingPnl,
            BigDecimal proprietaryMarketValue,
            BigDecimal consignmentMarketValue) {
    }
}

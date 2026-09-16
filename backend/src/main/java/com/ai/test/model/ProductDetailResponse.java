package com.ai.test.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 产品详情（spec 功能 9）：要素 / 费率 / 交易时段 / 净值走势 / 公告；
 * 代销产品净值标注外部来源与同步时间（数据以外部 TA 为准）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductDetailResponse(
        Long id,
        String productCode,
        String productName,
        String productType,
        String issuerName,
        String riskLevel,
        String category,
        Integer termDays,
        BigDecimal minPurchaseAmount,
        BigDecimal purchaseFeeRate,
        BigDecimal redemptionFeeRate,
        String expectedReturn,
        BigDecimal remainingQuota,
        LocalTime tradeStartTime,
        LocalTime tradeEndTime,
        String status,
        String consignmentRiskHint,
        NavSourceInfo navSource,
        List<NavPoint> navTrend,
        List<AnnouncementItem> announcements) {

    /** 净值来源标注：自营 INTERNAL（管理端发布）/ 代销 EXTERNAL（外部 TA 同步） */
    public record NavSourceInfo(String source, LocalDateTime syncedAt, String note) {
    }

    public record NavPoint(LocalDate date, BigDecimal nav) {
    }

    /** 产品公告（Mock 文案，演示用） */
    public record AnnouncementItem(LocalDate date, String title) {
    }
}

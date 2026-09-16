package com.ai.test.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 货架列表项：要素 + 最新净值；代销产品强制带风险提示文案（spec 功能 8） */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductSummaryResponse(
        Long id,
        String productCode,
        String productName,
        String productType,
        String issuerName,
        String riskLevel,
        String category,
        Integer termDays,
        BigDecimal minPurchaseAmount,
        String expectedReturn,
        BigDecimal latestNav,
        LocalDate latestNavDate,
        String status,
        String consignmentRiskHint) {

    /** 代销产品风险提示（避免与自营产品混同误导） */
    public static final String CONSIGNMENT_HINT =
            "本产品为代销产品，由 %s 发行与管理，银行不承担产品的投资、兑付和风险管理责任";

    public static ProductSummaryResponse from(
            com.ai.test.repository.entity.Product product,
            com.ai.test.repository.entity.ProductNav latestNav) {
        String hint = "CONSIGNMENT".equals(product.getProductType())
                ? CONSIGNMENT_HINT.formatted(product.getIssuerName())
                : null;
        return new ProductSummaryResponse(
                product.getId(),
                product.getProductCode(),
                product.getProductName(),
                product.getProductType(),
                product.getIssuerName(),
                product.getRiskLevel(),
                product.getCategory(),
                product.getTermDays(),
                product.getMinPurchaseAmount(),
                product.getExpectedReturn(),
                latestNav == null ? null : latestNav.getNav(),
                latestNav == null ? null : latestNav.getNavDate(),
                product.getStatus(),
                hint);
    }

    /** 货架列表响应 */
    public record ProductListResponse(int total, List<ProductSummaryResponse> products) {
    }
}

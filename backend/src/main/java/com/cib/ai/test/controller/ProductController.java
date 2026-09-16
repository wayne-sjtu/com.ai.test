package com.cib.ai.test.controller;

import com.cib.ai.test.model.ProductDetailResponse;
import com.cib.ai.test.model.ProductSummaryResponse;
import com.cib.ai.test.service.ProductShelfService;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** C 端产品货架端点（spec 功能 7/8/9）：统一货架筛选 + 产品详情 */
@RestController
@RequestMapping("/api/customer/products")
public class ProductController {

    private final ProductShelfService productShelfService;

    public ProductController(ProductShelfService productShelfService) {
        this.productShelfService = productShelfService;
    }

    /**
     * 统一货架：自营与代销同架展示，每只强制标注发行方与类型；
     * 筛选参数：productType / riskLevel / category / issuer / keyword / maxTermDays / maxMinAmount。
     */
    @GetMapping
    public ProductSummaryResponse.ProductListResponse list(
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String issuer,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer maxTermDays,
            @RequestParam(required = false) BigDecimal maxMinAmount) {
        return productShelfService.list(productType, riskLevel, category, issuer,
                keyword, maxTermDays, maxMinAmount);
    }

    /** 产品详情：要素/费率/净值走势/公告；代销带风险提示与外部来源标注 */
    @GetMapping("/{id}")
    public ProductDetailResponse detail(@PathVariable("id") Long id) {
        return productShelfService.detail(id);
    }
}

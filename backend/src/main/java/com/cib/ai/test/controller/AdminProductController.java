package com.cib.ai.test.controller;

import com.cib.ai.test.model.ProductSummaryResponse;
import com.cib.ai.test.service.ProductShelfService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端产品端点：复用货架查询供管理端使用（净值发布选择自营产品等）。
 * 双身份隔离下管理端 Session 不能访问 /api/customer/**，故独立暴露。
 */
@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductShelfService productShelfService;

    public AdminProductController(ProductShelfService productShelfService) {
        this.productShelfService = productShelfService;
    }

    /** 产品列表（productType=PROPRIETARY 供净值发布） */
    @GetMapping
    public ProductSummaryResponse.ProductListResponse list(
            @RequestParam(value = "productType", required = false) String productType,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return productShelfService.list(productType, null, null, null, keyword, null, null);
    }
}

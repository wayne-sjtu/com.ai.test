package com.cib.ai.test.service;

import com.cib.ai.test.model.ProductDetailResponse;
import com.cib.ai.test.model.ProductSummaryResponse;
import com.cib.ai.test.repository.ProductNavRepository;
import com.cib.ai.test.repository.ProductRepository;
import com.cib.ai.test.repository.entity.Product;
import com.cib.ai.test.repository.entity.ProductNav;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 产品货架用例（spec 功能 7/8/9）：
 * 统一货架多条件筛选 + 产品详情（费率/净值走势/公告），
 * 代销产品全程带发行方标识、风险提示与外部净值来源标注。
 */
@Service
public class ProductShelfService {

    private final ProductRepository productRepository;
    private final ProductNavRepository productNavRepository;

    public ProductShelfService(ProductRepository productRepository,
                               ProductNavRepository productNavRepository) {
        this.productRepository = productRepository;
        this.productNavRepository = productNavRepository;
    }

    /** 统一货架：筛选条件全部可空，附带每只产品最新净值 */
    public ProductSummaryResponse.ProductListResponse list(String productType, String riskLevel,
                                                           String category, String issuer,
                                                           String keyword, Integer maxTermDays,
                                                           BigDecimal maxMinAmount) {
        List<Product> products = productRepository.search(
                normalize(productType), normalize(riskLevel), normalize(category),
                normalize(issuer), normalize(keyword), maxTermDays, maxMinAmount);
        Map<String, ProductNav> latestNavByCode = productNavRepository.findLatestOfEachProduct().stream()
                .collect(Collectors.toMap(ProductNav::getProductCode, Function.identity()));
        List<ProductSummaryResponse> items = products.stream()
                .map(p -> ProductSummaryResponse.from(p, latestNavByCode.get(p.getProductCode())))
                .toList();
        return new ProductSummaryResponse.ProductListResponse(items.size(), items);
    }

    /** 产品详情：要素 + 费率 + 净值走势 + 来源标注 + 公告 */
    public ProductDetailResponse detail(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("产品不存在"));
        List<ProductNav> navs = productNavRepository.findByProductCodeOrderByNavDateAsc(
                product.getProductCode());

        ProductNav latest = navs.isEmpty() ? null : navs.get(navs.size() - 1);
        ProductDetailResponse.NavSourceInfo sourceInfo = buildSourceInfo(product, latest);
        String hint = "CONSIGNMENT".equals(product.getProductType())
                ? ProductSummaryResponse.CONSIGNMENT_HINT.formatted(product.getIssuerName())
                : null;
        return new ProductDetailResponse(
                product.getId(),
                product.getProductCode(),
                product.getProductName(),
                product.getProductType(),
                product.getIssuerName(),
                product.getRiskLevel(),
                product.getCategory(),
                product.getTermDays(),
                product.getMinPurchaseAmount(),
                product.getPurchaseFeeRate(),
                product.getRedemptionFeeRate(),
                product.getExpectedReturn(),
                product.getTotalQuota().subtract(product.getUsedQuota()),
                product.getTradeStartTime(),
                product.getTradeEndTime(),
                product.getStatus(),
                hint,
                sourceInfo,
                navs.stream().map(n -> new ProductDetailResponse.NavPoint(n.getNavDate(), n.getNav())).toList(),
                buildAnnouncements(product));
    }

    /** 代销净值来源为外部 TA（附同步时间），自营为管理端发布 */
    private ProductDetailResponse.NavSourceInfo buildSourceInfo(Product product, ProductNav latest) {
        if (latest == null) {
            return null;
        }
        boolean consignment = "CONSIGNMENT".equals(product.getProductType());
        String note = consignment
                ? "代销产品净值以发行机构（外部 TA）为准，以下为最近同步数据"
                : "自营产品净值由本行管理端发布";
        return new ProductDetailResponse.NavSourceInfo(
                latest.getSource(), latest.getSyncedAt(), note);
    }

    /** Mock 公告：按产品要素生成三条演示文案 */
    private List<ProductDetailResponse.AnnouncementItem> buildAnnouncements(Product product) {
        String code = product.getProductCode();
        return List.of(
                new ProductDetailResponse.AnnouncementItem(
                        latestNavDate(product).minusDays(1), code + " 定期管理报告"),
                new ProductDetailResponse.AnnouncementItem(
                        latestNavDate(product).minusDays(10), code + " 净值变动说明公告"),
                new ProductDetailResponse.AnnouncementItem(
                        latestNavDate(product).minusDays(25), code + " 产品成立公告"));
    }

    private java.time.LocalDate latestNavDate(Product product) {
        List<ProductNav> navs = productNavRepository.findByProductCodeOrderByNavDateAsc(
                product.getProductCode());
        return navs.isEmpty() ? java.time.LocalDate.now() : navs.get(navs.size() - 1).getNavDate();
    }

    private String normalize(String param) {
        return (param == null || param.isBlank()) ? null : param.trim();
    }
}

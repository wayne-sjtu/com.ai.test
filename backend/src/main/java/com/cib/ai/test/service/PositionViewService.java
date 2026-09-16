package com.cib.ai.test.service;

import com.cib.ai.test.domain.OrderStateMachine;
import com.cib.ai.test.domain.ValuationCalculator;
import com.cib.ai.test.model.PositionViewResponse;
import com.cib.ai.test.model.PositionViewResponse.InFlightItem;
import com.cib.ai.test.model.PositionViewResponse.PositionItem;
import com.cib.ai.test.model.PositionViewResponse.Summary;
import com.cib.ai.test.repository.OrderRepository;
import com.cib.ai.test.repository.PositionRepository;
import com.cib.ai.test.repository.ProductNavRepository;
import com.cib.ai.test.repository.ProductRepository;
import com.cib.ai.test.repository.entity.Order;
import com.cib.ai.test.repository.entity.Position;
import com.cib.ai.test.repository.entity.Product;
import com.cib.ai.test.repository.entity.ProductNav;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 统一持仓视图用例（spec 用户故事 25/26，设计文档 4.1 `/positions`）：
 * 自营/代销同视图区分；实时估值（份额 × 最新净值，ValuationCalculator 纯函数统一口径）；
 * 代销净值来源标注（EXTERNAL + 同步时间，数据以外部 TA 为准）；含在途资产与分区汇总。
 * 产品与净值批量查询（内存 Map），消除循环内 N+1。
 */
@Service
public class PositionViewService {

        private final PositionRepository positionRepository;
        private final ProductRepository productRepository;
        private final ProductNavRepository productNavRepository;
        private final OrderRepository orderRepository;

        public PositionViewService(PositionRepository positionRepository,
                        ProductRepository productRepository,
                        ProductNavRepository productNavRepository,
                        OrderRepository orderRepository) {
                this.positionRepository = positionRepository;
                this.productRepository = productRepository;
                this.productNavRepository = productNavRepository;
                this.orderRepository = orderRepository;
        }

        public PositionViewResponse view(String customerNo) {
                List<PositionItem> items = new ArrayList<>();
                BigDecimal totalMarket = BigDecimal.ZERO;
                BigDecimal totalCost = BigDecimal.ZERO;
                BigDecimal proprietaryMarket = BigDecimal.ZERO;
                BigDecimal consignmentMarket = BigDecimal.ZERO;

                List<Position> positions = positionRepository.findByCustomerNo(customerNo);

                List<Order> inFlightOrders = orderRepository.findByCustomerNoOrderByIdDesc(customerNo).stream()
                                .filter(o -> OrderStateMachine.inFlight(o.getStatus()))
                                .toList();

                // 汇总涉及的产品代码（持仓 + 在途订单），一次性批量查询
                Set<String> codes = new HashSet<>();
                positions.stream()
                                .filter(p -> p.getShares().signum() > 0 || p.getFrozenShares().signum() > 0)
                                .forEach(p -> codes.add(p.getProductCode()));
                inFlightOrders.forEach(o -> codes.add(o.getProductCode()));

                Map<String, Product> products = codes.isEmpty() ? Map.of()
                                : productRepository.findByProductCodeIn(codes).stream()
                                                .collect(Collectors.toMap(Product::getProductCode,
                                                                Function.identity()));
                Map<String, ProductNav> navs = codes.isEmpty() ? Map.of()
                                : productNavRepository.findLatestByProductCodeIn(codes).stream()
                                                .collect(Collectors.toMap(ProductNav::getProductCode,
                                                                Function.identity()));

                for (Position position : positions) {
                        if (position.getShares().signum() <= 0
                                        && position.getFrozenShares().signum() <= 0) {
                                continue; // 零持仓不展示
                        }
                        Product product = products.get(position.getProductCode());
                        ProductNav latestNav = navs.get(position.getProductCode());
                        BigDecimal nav = latestNav != null ? latestNav.getNav() : BigDecimal.ONE;
                        BigDecimal marketValue = ValuationCalculator.marketValue(position.getShares(), nav);
                        BigDecimal floatingPnl = ValuationCalculator.floatingPnl(marketValue,
                                        position.getCostAmount());

                        totalMarket = totalMarket.add(marketValue);
                        totalCost = totalCost.add(position.getCostAmount());
                        if (product != null && "PROPRIETARY".equals(product.getProductType())) {
                                proprietaryMarket = proprietaryMarket.add(marketValue);
                        } else {
                                consignmentMarket = consignmentMarket.add(marketValue);
                        }

                        items.add(new PositionItem(
                                        position.getProductCode(),
                                        product != null ? product.getProductName() : position.getProductCode(),
                                        product != null ? product.getProductType() : null,
                                        product != null ? product.getRiskLevel() : null,
                                        product != null ? product.getCategory() : null,
                                        position.getShares(),
                                        position.getFrozenShares(),
                                        position.getShares().subtract(position.getFrozenShares()),
                                        nav,
                                        latestNav != null ? latestNav.getSource() : null,
                                        latestNav != null ? latestNav.getSyncedAt() : null,
                                        position.getCostAmount(),
                                        marketValue,
                                        floatingPnl,
                                        ValuationCalculator.returnRate(floatingPnl, position.getCostAmount())));
                }

                List<InFlightItem> inFlights = inFlightOrders.stream()
                                .map(o -> toInFlight(o, products))
                                .toList();

                return new PositionViewResponse(items, inFlights, new Summary(
                                totalMarket, totalCost,
                                ValuationCalculator.floatingPnl(totalMarket, totalCost),
                                proprietaryMarket, consignmentMarket));
        }

        private InFlightItem toInFlight(Order order, Map<String, Product> products) {
                Product product = products.get(order.getProductCode());
                return new InFlightItem(
                                order.getOrderNo(),
                                order.getProductCode(),
                                product != null ? product.getProductName() : order.getProductCode(),
                                order.getProductType(),
                                order.getOrderType(),
                                order.getStatus(),
                                order.getAmount(),
                                order.getShares());
        }
}

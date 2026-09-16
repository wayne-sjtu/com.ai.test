package com.cib.ai.test.service;

import com.cib.ai.test.model.AdminOrderResponse;
import com.cib.ai.test.model.OrderEventResponse;
import com.cib.ai.test.repository.OrderEventRepository;
import com.cib.ai.test.repository.OrderRepository;
import com.cib.ai.test.repository.entity.Order;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * 管理端订单查询用例（spec 用户故事 32）：
 * 统一订单列表（自营/代销同视图，筛选维度自由组合）+ 订单全生命周期事件轨迹
 * （含 TA 标识 INTA/EXTA 与报文流水号，路由链路可追溯）。
 */
@Service
public class AdminOrderService {

    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;

    public AdminOrderService(OrderRepository orderRepository,
                             OrderEventRepository orderEventRepository) {
        this.orderRepository = orderRepository;
        this.orderEventRepository = orderEventRepository;
    }

    /** 统一订单查询：productType / orderType / status / customerNo / productCode 任意组合筛选 */
    public List<AdminOrderResponse> search(String productType, String orderType, String status,
                                           String customerNo, String productCode) {
        Specification<Order> spec = Specification.where(null);
        if (productType != null && !productType.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("productType"), productType));
        }
        if (orderType != null && !orderType.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("orderType"), orderType));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (customerNo != null && !customerNo.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("customerNo"), customerNo));
        }
        if (productCode != null && !productCode.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("productCode"), productCode));
        }
        return orderRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(AdminOrderResponse::from)
                .toList();
    }

    /** 订单事件轨迹（append-only，按发生顺序） */
    public List<OrderEventResponse> events(String orderNo) {
        requireOrder(orderNo);
        return orderEventRepository.findByOrderNoOrderByCreatedAtAsc(orderNo).stream()
                .map(OrderEventResponse::from)
                .toList();
    }

    private void requireOrder(String orderNo) {
        orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new TradeException("ORDER_NOT_FOUND", "订单不存在"));
    }
}

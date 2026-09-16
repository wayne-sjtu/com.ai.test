package com.ai.test.controller;

import com.ai.test.model.ApiError;
import com.ai.test.model.OrderResponse;
import com.ai.test.model.PurchaseRequest;
import com.ai.test.model.RedeemRequest;
import com.ai.test.service.OrderService;
import com.ai.test.service.PurchaseOutcome;
import com.ai.test.service.TradeException;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** C 端订单端点（spec 功能 10/11/12）：申购/赎回下单 + 撤单 + 我的订单列表 */
@RestController
@RequestMapping("/api/customer/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 申购下单：失败单留痕返回 400（body 含 orderNo 便于前端定位失败订单）。
     * 幂等：同一 clientRequestId 重复提交返回原订单（200）。
     */
    @PostMapping("/purchase")
    public ResponseEntity<?> purchase(@Valid @RequestBody PurchaseRequest request,
            HttpSession session) {
        String customerNo = CustomerAccountController.customerNo(session);
        PurchaseOutcome outcome = orderService.purchase(customerNo, request);
        if (outcome.failed()) {
            return ResponseEntity.badRequest().body(new ApiError(
                    outcome.errorCode(), outcome.errorMessage(),
                    List.of("orderNo: " + outcome.order().getOrderNo())));
        }
        return ResponseEntity.ok(OrderResponse.from(outcome.order()));
    }

    /**
     * 赎回下单：非巨额即时确认（本行同步 / 代销回调），巨额整单 PENDING_QUEUE 延期确认。
     * 失败单留痕返回 400；clientRequestId 幂等。
     */
    @PostMapping("/redeem")
    public ResponseEntity<?> redeem(@Valid @RequestBody RedeemRequest request,
            HttpSession session) {
        String customerNo = CustomerAccountController.customerNo(session);
        PurchaseOutcome outcome = orderService.redeem(customerNo, request);
        if (outcome.failed()) {
            return ResponseEntity.badRequest().body(new ApiError(
                    outcome.errorCode(), outcome.errorMessage(),
                    List.of("orderNo: " + outcome.order().getOrderNo())));
        }
        return ResponseEntity.ok(OrderResponse.from(outcome.order()));
    }

    /** 撤单：可撤时段内（产品交易时段、TA 确认前）的在途订单 */
    @PostMapping("/{orderNo}/cancel")
    public OrderResponse cancel(@PathVariable("orderNo") String orderNo, HttpSession session) {
        return orderService.cancel(CustomerAccountController.customerNo(session), orderNo);
    }

    @GetMapping
    public List<OrderResponse> myOrders(HttpSession session) {
        return orderService.listByCustomer(CustomerAccountController.customerNo(session));
    }
}

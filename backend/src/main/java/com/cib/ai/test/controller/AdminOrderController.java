package com.cib.ai.test.controller;

import com.cib.ai.test.model.AdminOrderResponse;
import com.cib.ai.test.model.OrderEventResponse;
import com.cib.ai.test.service.AdminOrderService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端订单端点（spec 用户故事 32）：
 * 统一订单列表（自营/代销、全生命周期状态、TA 路由链路）+ 事件轨迹查询。
 * 管理端 Session 由 AuthInterceptor 保证（/api/admin/** 仅运营身份可访问）。
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    @GetMapping
    public List<AdminOrderResponse> search(
            @RequestParam(value = "productType", required = false) String productType,
            @RequestParam(value = "orderType", required = false) String orderType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "customerNo", required = false) String customerNo,
            @RequestParam(value = "productCode", required = false) String productCode) {
        return adminOrderService.search(productType, orderType, status, customerNo, productCode);
    }

    @GetMapping("/{orderNo}/events")
    public List<OrderEventResponse> events(@PathVariable("orderNo") String orderNo) {
        return adminOrderService.events(orderNo);
    }
}

package com.cib.ai.test.controller;

import com.cib.ai.test.model.OrderResponse;
import com.cib.ai.test.repository.CapitalFlowRepository;
import com.cib.ai.test.repository.OrderRepository;
import com.cib.ai.test.repository.entity.CapitalFlow;
import com.cib.ai.test.repository.entity.Order;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端流水查询与导出（spec 用户故事 27，设计文档 4.1 `/flows`）：
 * 交易流水 = 订单维度（自营/代销分开归档筛选，format=csv 导出）；
 * 资金流水 = 资金动作维度（对账锚点 balanceAfter，供订单-流水-持仓三方核对演示）。
 */
@RestController
@RequestMapping("/api/customer")
public class FlowController {

        private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        private final OrderRepository orderRepository;
        private final CapitalFlowRepository capitalFlowRepository;

        public FlowController(OrderRepository orderRepository,
                        CapitalFlowRepository capitalFlowRepository) {
                this.orderRepository = orderRepository;
                this.capitalFlowRepository = capitalFlowRepository;
        }

        /** 交易流水：productType/orderType/status 筛选自营代销分档；format=csv 导出 */
        @GetMapping("/flows")
        public Object tradeFlows(
                        @RequestParam(value = "productType", required = false) String productType,
                        @RequestParam(value = "orderType", required = false) String orderType,
                        @RequestParam(value = "status", required = false) String status,
                        @RequestParam(value = "format", defaultValue = "json") String format,
                        HttpSession session,
                        HttpServletResponse response) throws IOException {
                String customerNo = CustomerAccountController.customerNo(session);
                List<Order> orders = orderRepository.findByCustomerNoOrderByIdDesc(customerNo).stream()
                                .filter(o -> productType == null || productType.isBlank()
                                                || productType.equals(o.getProductType()))
                                .filter(o -> orderType == null || orderType.isBlank()
                                                || orderType.equals(o.getOrderType()))
                                .filter(o -> status == null || status.isBlank()
                                                || status.equals(o.getStatus()))
                                .toList();

                if ("csv".equalsIgnoreCase(format)) {
                        writeTradeCsv(orders, response);
                        return null;
                }
                return Map.of(
                                "total", orders.size(),
                                "productType", productType == null ? "ALL" : productType,
                                "flows", orders.stream().map(OrderResponse::from).toList());
        }

        /** 资金流水（FREEZE/DEDUCT/UNFREEZE/RETURN，含动作后余额对账锚点） */
        @GetMapping("/capital-flows")
        public Map<String, Object> capitalFlows(HttpSession session) {
                String customerNo = CustomerAccountController.customerNo(session);
                List<CapitalFlow> flows = capitalFlowRepository.findByCustomerNoOrderByCreatedAtDesc(customerNo);
                return Map.of("total", flows.size(), "flows", flows);
        }

        private void writeTradeCsv(List<Order> orders, HttpServletResponse response)
                        throws IOException {
                response.setContentType("text/csv;charset=UTF-8");
                response.setHeader("Content-Disposition",
                                "attachment; filename=trade-flows.csv");
                PrintWriter writer = response.getWriter();
                // BOM：Excel 中文兼容
                writer.write('\ufeff');
                writer.println("订单号,客户号,产品代码,产品类型,订单类型,金额,份额,费用,状态,TA流水号,创建时间");
                for (Order o : orders) {
                        writer.println(String.join(",",
                                        csv(o.getOrderNo()), csv(o.getCustomerNo()), csv(o.getProductCode()),
                                        "PROPRIETARY".equals(o.getProductType()) ? "自营" : "代销",
                                        "PURCHASE".equals(o.getOrderType()) ? "申购" : "赎回",
                                        csvDecimal(o.getAmount()), csvDecimal(o.getShares()),
                                        csvDecimal(o.getFee()), csv(o.getStatus()),
                                        csv(o.getTaSerialNo()),
                                        csvTime(o.getCreatedAt())));
                }
        }

        private String csv(String value) {
                return value == null ? "" : value;
        }

        private String csvDecimal(BigDecimal value) {
                return value == null ? "" : value.toPlainString();
        }

        private String csvTime(LocalDateTime time) {
                return time == null ? "" : CSV_TIME.format(time);
        }
}

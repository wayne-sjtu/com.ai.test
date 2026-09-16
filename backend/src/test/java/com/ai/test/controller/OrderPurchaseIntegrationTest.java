package com.ai.test.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.test.repository.CapitalAccountRepository;
import com.ai.test.repository.CapitalFlowRepository;
import com.ai.test.repository.OrderEventRepository;
import com.ai.test.repository.OrderRepository;
import com.ai.test.repository.PositionRepository;
import com.ai.test.repository.ProductRepository;
import com.ai.test.service.ExternalTaConfirmService;
import com.ai.test.taclient.ExternalTaClient;
import com.ai.test.taclient.FaultMode;
import com.ai.test.taclient.TaCallback;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 交易主链路集成测试（spec 功能 10/11，端到端核心）：
 * 自营同步结算全链路、代销异步受理 + 回调结算、幂等、失败单留痕、
 * 校验链各节点拦截（适当性/额度/余额/起购/时段/双录/签约）、TA 故障（拒绝/超时）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderPurchaseIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private EntityManager em;

        @Autowired
        private OrderRepository orderRepository;

        @Autowired
        private OrderEventRepository orderEventRepository;

        @Autowired
        private CapitalAccountRepository capitalAccountRepository;

        @Autowired
        private CapitalFlowRepository capitalFlowRepository;

        @Autowired
        private PositionRepository positionRepository;

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private ExternalTaClient externalTaClient;

        @Autowired
        private ExternalTaConfirmService externalTaConfirmService;

        @BeforeEach
        void 放宽交易时段避免测试受运行时刻影响() {
                em.createQuery("update Product p set p.tradeStartTime = :st, p.tradeEndTime = :et")
                                .setParameter("st", LocalTime.of(0, 0))
                                .setParameter("et", LocalTime.of(23, 59, 59))
                                .executeUpdate();
        }

        private MockHttpSession login(String mobile) throws Exception {
                MvcResult result = mockMvc.perform(post("/api/customer/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"mobile":"%s","password":"Passw0rd!","channelCode":"PC_WEB"}
                                                """.formatted(mobile)))
                                .andExpect(status().isOk())
                                .andReturn();
                return (MockHttpSession) result.getRequest().getSession();
        }

        private MvcResult purchase(MockHttpSession session, String productCode, String amount,
                        boolean confirmRisk, String clientRequestId) throws Exception {
                return mockMvc.perform(post("/api/customer/orders/purchase")
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"productCode":"%s","amount":%s,"confirmRisk":%s,"clientRequestId":"%s"}
                                                """.formatted(productCode, amount, confirmRisk, clientRequestId)))
                                .andReturn();
        }

        private com.ai.test.repository.entity.Order orderByRequest(String clientRequestId) {
                return orderRepository.findByClientRequestId(clientRequestId).orElseThrow();
        }

        @Test
        void 自营申购全链路_同步确认直至结算() throws Exception {
                MockHttpSession session = login("13800000004");
                BigDecimal quotaBefore = productRepository.findByProductCode("P-PR-01").orElseThrow()
                                .getUsedQuota();

                MvcResult result = purchase(session, "P-PR-01", "10000", false, "T-CASE-PROP-1");
                // language=JSON
                expect(result, status().isOk());

                var order = orderByRequest("T-CASE-PROP-1");
                org.assertj.core.api.Assertions.assertThat(order.getStatus()).isEqualTo("SETTLED");
                org.assertj.core.api.Assertions.assertThat(order.getShares())
                                .isEqualByComparingTo("10000.00");
                org.assertj.core.api.Assertions.assertThat(order.getFee()).isEqualByComparingTo("0.00");
                org.assertj.core.api.Assertions.assertThat(order.getTaSerialNo()).startsWith("INTA-");

                // 事件轨迹 5 步
                var events = orderEventRepository.findByOrderNoOrderByCreatedAtAsc(order.getOrderNo());
                org.assertj.core.api.Assertions.assertThat(events)
                                .extracting("eventType")
                                .containsExactly("CREATE", "SUBMIT", "TA_ACCEPT", "CONFIRM", "SETTLE");

                // 资金：可用 -10000、冻结不变；流水 FREEZE + DEDUCT
                var capital = capitalAccountRepository.findByCustomerNo("CUST2026000004").orElseThrow();
                org.assertj.core.api.Assertions.assertThat(capital.getAvailableBalance())
                                .isEqualByComparingTo("859100.00");
                org.assertj.core.api.Assertions.assertThat(capital.getFrozenBalance())
                                .isEqualByComparingTo("50000.00");
                org.assertj.core.api.Assertions.assertThat(
                                capitalFlowRepository.findByOrderNoAndActionType(order.getOrderNo(), "FREEZE"))
                                .isPresent();
                org.assertj.core.api.Assertions.assertThat(
                                capitalFlowRepository.findByOrderNoAndActionType(order.getOrderNo(), "DEDUCT"))
                                .isPresent();

                // 持仓：50000 + 10000
                var position = positionRepository
                                .findByCustomerNoAndProductCode("CUST2026000004", "P-PR-01").orElseThrow();
                org.assertj.core.api.Assertions.assertThat(position.getShares())
                                .isEqualByComparingTo("60000.00");

                // 额度占用 +10000
                org.assertj.core.api.Assertions.assertThat(
                                productRepository.findByProductCode("P-PR-01").orElseThrow().getUsedQuota())
                                .isEqualByComparingTo(quotaBefore.add(new BigDecimal("10000")));
        }

        @Test
        void 幂等_同一clientRequestId返回原订单() throws Exception {
                MockHttpSession session = login("13800000004");
                MvcResult first = purchase(session, "P-PR-01", "5000", false, "T-CASE-IDEM-1");
                expect(first, status().isOk());
                long countBefore = orderRepository.count();

                MvcResult second = purchase(session, "P-PR-01", "5000", false, "T-CASE-IDEM-1");
                expect(second, status().isOk(),
                                jsonPath("$.orderNo").value(orderByRequest("T-CASE-IDEM-1").getOrderNo()));
                org.assertj.core.api.Assertions.assertThat(orderRepository.count()).isEqualTo(countBefore);
        }

        @Test
        void 起购金额不足_失败单留痕返回400() throws Exception {
                MockHttpSession session = login("13800000004");
                MvcResult result = purchase(session, "P-PR-02", "100", false, "T-CASE-MIN-1");
                expect(result, status().isBadRequest(), jsonPath("$.code").value("MIN_AMOUNT"),
                                jsonPath("$.reasons[0]").isNotEmpty());

                var order = orderByRequest("T-CASE-MIN-1");
                org.assertj.core.api.Assertions.assertThat(order.getStatus()).isEqualTo("FAILED");
        }

        @Test
        void 适当性拦截_C1买R3() throws Exception {
                MockHttpSession session = login("13800000001");
                MvcResult result = purchase(session, "P-PR-02", "20000", false, "T-CASE-SUIT-BLOCK");
                expect(result, status().isBadRequest(), jsonPath("$.code").value("SUITABILITY_BLOCKED"));
                org.assertj.core.api.Assertions.assertThat(orderByRequest("T-CASE-SUIT-BLOCK").getStatus())
                                .isEqualTo("FAILED");
        }

        @Test
        void 适当性二次确认_C3买R4未确认拦截() throws Exception {
                MockHttpSession session = login("13800000003");
                MvcResult result = purchase(session, "P-PR-03", "100000", false, "T-CASE-SUIT-CONFIRM");
                expect(result, status().isBadRequest(), jsonPath("$.code").value("SUITABILITY_CONFIRM_REQUIRED"));
        }

        @Test
        void 额度不足_P_PR_04仅剩2万() throws Exception {
                // c5 为 C5（×R5 = PASS），避免撞适当性二次确认档
                MockHttpSession session = login("13800000005");
                MvcResult result = purchase(session, "P-PR-04", "100000", false, "T-CASE-QUOTA-1");
                expect(result, status().isBadRequest(), jsonPath("$.code").value("QUOTA_EXCEEDED"));
        }

        @Test
        void 余额不足_资金校验拦截() throws Exception {
                MockHttpSession session = login("13800000004");
                MvcResult result = purchase(session, "P-PR-01", "900000", false, "T-CASE-BAL-1");
                expect(result, status().isBadRequest(), jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
                // 失败单留痕 + 额度未被占用（余额校验先于额度或额度已释放）
                var order = orderByRequest("T-CASE-BAL-1");
                org.assertj.core.api.Assertions.assertThat(order.getStatus()).isEqualTo("FAILED");
        }

        @Test
        void 交易时段外拦截() throws Exception {
                em.createQuery(
                                "update Product p set p.tradeStartTime = :st, p.tradeEndTime = :et "
                                                + "where p.productCode = 'P-PR-02'")
                                .setParameter("st", LocalTime.of(0, 0))
                                .setParameter("et", LocalTime.of(1, 0))
                                .executeUpdate();
                MockHttpSession session = login("13800000004");
                MvcResult result = purchase(session, "P-PR-02", "20000", false, "T-CASE-WINDOW-1");
                expect(result, status().isBadRequest(), jsonPath("$.code").value("TRADE_WINDOW_CLOSED"));
        }

        @Test
        void 未签约客户拦截() throws Exception {
                MockHttpSession session = login("13800000007");
                MvcResult result = purchase(session, "P-PR-01", "1000", false, "T-CASE-UNSIGNED-1");
                expect(result, status().isBadRequest(), jsonPath("$.code").value("ACCOUNT_NOT_SIGNED"));
        }

        @Test
        void 代销R4双录缺失拦截_补录后受理并回调结算() throws Exception {
                MockHttpSession session = login("13800000004");
                externalTaClient.setFaultMode(FaultMode.DELAY_CONFIRM);
                try {
                        // 双录缺失 → 拦截
                        MvcResult blocked = purchase(session, "P-CS-03", "100000", false, "T-CASE-DUAL-1");
                        expect(blocked, status().isBadRequest(), jsonPath("$.code").value("DUAL_RECORD_REQUIRED"));

                        // Mock 双录上传后 → TA_ACCEPTED（异步延迟确认，避免竞态）
                        mockMvc.perform(post("/api/customer/dual-records/mock-upload")
                                        .session(session).contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"productCode\":\"P-CS-03\"}"))
                                        .andExpect(status().isOk());
                        MvcResult accepted = purchase(session, "P-CS-03", "100000", false, "T-CASE-DUAL-2");
                        expect(accepted, status().isOk(), jsonPath("$.status").value("TA_ACCEPTED"),
                                        jsonPath("$.taSerialNo").isNotEmpty());

                        var order = orderByRequest("T-CASE-DUAL-2");
                        // 手动触发回调（同线程同事务，模拟外部 TA 确认）
                        externalTaConfirmService.handle(new TaCallback("TEST-EXTA-SN-001",
                                        order.getOrderNo(), "CONFIRMED", new BigDecimal("99700.00"), "测试回调"));
                        org.assertj.core.api.Assertions.assertThat(order.getStatus()).isEqualTo("SETTLED");
                        org.assertj.core.api.Assertions.assertThat(order.getShares())
                                        .isEqualByComparingTo("99700.00");
                        // 资金扣划 + 持仓入账
                        org.assertj.core.api.Assertions.assertThat(
                                        capitalFlowRepository.findByOrderNoAndActionType(
                                                        order.getOrderNo(), "DEDUCT"))
                                        .isPresent();
                        var position = positionRepository
                                        .findByCustomerNoAndProductCode("CUST2026000004", "P-CS-03").orElseThrow();
                        org.assertj.core.api.Assertions.assertThat(position.getShares())
                                        .isEqualByComparingTo("99700.00");

                        // 幂等：同流水号重复回调 + 不同流水号重复确认均不重复入账
                        externalTaConfirmService.handle(new TaCallback("TEST-EXTA-SN-001",
                                        order.getOrderNo(), "CONFIRMED", new BigDecimal("99700.00"), "重复回调"));
                        externalTaConfirmService.handle(new TaCallback("TEST-EXTA-SN-002",
                                        order.getOrderNo(), "CONFIRMED", new BigDecimal("99700.00"), "不同流水号"));
                        org.assertj.core.api.Assertions.assertThat(positionRepository
                                        .findByCustomerNoAndProductCode("CUST2026000004", "P-CS-03")
                                        .orElseThrow().getShares())
                                        .isEqualByComparingTo("99700.00");
                } finally {
                        externalTaClient.setFaultMode(FaultMode.NORMAL);
                }
        }

        @Test
        void 外部TA拒绝_失败留痕并解冻资金() throws Exception {
                MockHttpSession session = login("13800000004");
                BigDecimal quotaBefore = productRepository.findByProductCode("P-CS-01").orElseThrow()
                                .getUsedQuota();
                externalTaClient.setFaultMode(FaultMode.REJECT);
                try {
                        MvcResult result = purchase(session, "P-CS-01", "10000", false, "T-CASE-REJECT-1");
                        expect(result, status().isBadRequest(), jsonPath("$.code").value("TA_REJECTED"));
                        var order = orderByRequest("T-CASE-REJECT-1");
                        org.assertj.core.api.Assertions.assertThat(order.getStatus()).isEqualTo("FAILED");
                        // 资金解冻：可用余额不变
                        var capital = capitalAccountRepository.findByCustomerNo("CUST2026000004").orElseThrow();
                        org.assertj.core.api.Assertions.assertThat(capital.getAvailableBalance())
                                        .isEqualByComparingTo("869100.00");
                        // 额度释放
                        org.assertj.core.api.Assertions.assertThat(
                                        productRepository.findByProductCode("P-CS-01").orElseThrow().getUsedQuota())
                                        .isEqualByComparingTo(quotaBefore);
                } finally {
                        externalTaClient.setFaultMode(FaultMode.NORMAL);
                }
        }

        @Test
        void 外部TA超时_失败留痕并解冻资金() throws Exception {
                MockHttpSession session = login("13800000004");
                externalTaClient.setFaultMode(FaultMode.TIMEOUT);
                try {
                        MvcResult result = purchase(session, "P-CS-01", "10000", false, "T-CASE-TIMEOUT-1");
                        expect(result, status().isBadRequest(), jsonPath("$.code").value("TA_COMMUNICATION_ERROR"));
                        var order = orderByRequest("T-CASE-TIMEOUT-1");
                        org.assertj.core.api.Assertions.assertThat(order.getStatus()).isEqualTo("FAILED");
                        var capital = capitalAccountRepository.findByCustomerNo("CUST2026000004").orElseThrow();
                        org.assertj.core.api.Assertions.assertThat(capital.getAvailableBalance())
                                        .isEqualByComparingTo("869100.00");
                } finally {
                        externalTaClient.setFaultMode(FaultMode.NORMAL);
                }
        }

        @Test
        void 我的订单列表_含失败留痕单() throws Exception {
                MockHttpSession session = login("13800000004");
                MvcResult failed = purchase(session, "P-PR-02", "100", false, "T-CASE-LIST-1");
                expect(failed, status().isBadRequest());
                mockMvc.perform(get("/api/customer/orders").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[?(@.status=='FAILED')]").isNotEmpty());
                org.assertj.core.api.Assertions.assertThat(
                                orderRepository.findByClientRequestId("T-CASE-LIST-1").orElseThrow()
                                                .getStatus())
                                .isEqualTo("FAILED");
        }

        /** 直接对已执行的 MvcResult 断言（purchase 辅助返回 MvcResult） */
        private void expect(MvcResult result,
                        org.springframework.test.web.servlet.ResultMatcher... matchers)
                        throws Exception {
                for (org.springframework.test.web.servlet.ResultMatcher matcher : matchers) {
                        matcher.match(result);
                }
        }
}

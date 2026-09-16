package com.ai.test.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.test.repository.CapitalAccountRepository;
import com.ai.test.repository.OrderEventRepository;
import com.ai.test.repository.OrderRepository;
import com.ai.test.repository.PositionRepository;
import com.ai.test.repository.ProductNavRepository;
import com.ai.test.repository.ProductRepository;
import com.ai.test.repository.entity.ProductNav;
import com.ai.test.service.ExternalTaConfirmService;
import com.ai.test.service.OrderService;
import com.ai.test.taclient.TaCallback;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * 赎回链路与撤单集成测试（spec 功能 10/11/12，设计文档 5.2）：
 * 自营赎回同步 REDEEMED、巨额赎回 PENDING_QUEUE 延期确认、代销赎回异步回调、
 * 撤单（申购/赎回两型）资金份额回补、不可撤/越权/份额不足拦截。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderRedeemCancelIntegrationTest {

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
        private PositionRepository positionRepository;

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private ProductNavRepository productNavRepository;

        @Autowired
        private ExternalTaConfirmService externalTaConfirmService;

        @Autowired
        private OrderService orderService;

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

        private MvcResult redeem(MockHttpSession session, String productCode, String shares,
                        String clientRequestId) throws Exception {
                return mockMvc.perform(post("/api/customer/orders/redeem")
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"productCode":"%s","shares":%s,"clientRequestId":"%s"}
                                                """.formatted(productCode, shares, clientRequestId)))
                                .andReturn();
        }

        private MvcResult cancel(MockHttpSession session, String orderNo) throws Exception {
                return mockMvc.perform(post("/api/customer/orders/" + orderNo + "/cancel")
                                .session(session))
                                .andReturn();
        }

        private void expect(MvcResult result,
                        org.springframework.test.web.servlet.ResultMatcher... matchers) throws Exception {
                for (org.springframework.test.web.servlet.ResultMatcher matcher : matchers) {
                        matcher.match(result);
                }
        }

        private BigDecimal latestNav(String productCode) {
                ProductNav nav = productNavRepository.findFirstByProductCodeOrderByNavDateDesc(productCode);
                return nav.getNav();
        }

        @Test
        void 自营赎回全链路_同步确认直至REDEEMED() throws Exception {
                MockHttpSession session = login("13800000005");
                BigDecimal quotaBefore = productRepository.findByProductCode("P-PR-03").orElseThrow()
                                .getUsedQuota();
                BigDecimal availableBefore = capitalAccountRepository
                                .findByCustomerNo("CUST2026000005").orElseThrow().getAvailableBalance();
                BigDecimal nav = latestNav("P-PR-03");

                MvcResult result = redeem(session, "P-PR-03", "5000", "T-RD-PROP-1");
                expect(result, status().isOk(), jsonPath("$.status").value("REDEEMED"));

                var order = orderRepository.findByClientRequestId("T-RD-PROP-1").orElseThrow();
                // 回款 = 5000 × 最新净值 − 0.5% 赎回费
                BigDecimal gross = new BigDecimal("5000").multiply(nav).setScale(2, RoundingMode.HALF_UP);
                BigDecimal fee = gross.multiply(new BigDecimal("0.0050")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal net = gross.subtract(fee);
                org.assertj.core.api.Assertions.assertThat(order.getAmount()).isEqualByComparingTo(gross);
                org.assertj.core.api.Assertions.assertThat(order.getFee()).isEqualByComparingTo(fee);
                org.assertj.core.api.Assertions.assertThat(order.getTaSerialNo()).startsWith("INTA-");

                // 事件轨迹：CREATE → SUBMIT → TA_ACCEPT → CONFIRM → REDEEM
                var events = orderEventRepository.findByOrderNoOrderByCreatedAtAsc(order.getOrderNo());
                org.assertj.core.api.Assertions.assertThat(events)
                                .extracting("eventType")
                                .containsExactly("CREATE", "SUBMIT", "TA_ACCEPT", "CONFIRM", "REDEEM");

                // 持仓：105300 − 5000 = 100300，冻结归零，成本按比例结转
                var position = positionRepository
                                .findByCustomerNoAndProductCode("CUST2026000005", "P-PR-03").orElseThrow();
                org.assertj.core.api.Assertions.assertThat(position.getShares())
                                .isEqualByComparingTo("100300.00");
                org.assertj.core.api.Assertions.assertThat(position.getFrozenShares())
                                .isEqualByComparingTo("10000.00"); // 种子在途赎回单的冻结保留

                // 资金回款到可用余额
                org.assertj.core.api.Assertions.assertThat(capitalAccountRepository
                                .findByCustomerNo("CUST2026000005").orElseThrow().getAvailableBalance())
                                .isEqualByComparingTo(availableBefore.add(net));

                // 赎回确认释放销售额度
                org.assertj.core.api.Assertions.assertThat(
                                productRepository.findByProductCode("P-PR-03").orElseThrow().getUsedQuota())
                                .isEqualByComparingTo(quotaBefore.subtract(gross));
        }

        @Test
        void 巨额赎回超阈值进入PENDING_QUEUE_调度确认后REDEEMED() throws Exception {
                MockHttpSession session = login("13800000005");
                // P-PR-03 总份额 105300，阈值 10% = 10530；赎 20000 超限 → 整单延期
                MvcResult result = redeem(session, "P-PR-03", "20000", "T-RD-LARGE-1");
                expect(result, status().isOk(), jsonPath("$.status").value("PENDING_QUEUE"));

                var order = orderRepository.findByClientRequestId("T-RD-LARGE-1").orElseThrow();
                var events = orderEventRepository.findByOrderNoOrderByCreatedAtAsc(order.getOrderNo());
                org.assertj.core.api.Assertions.assertThat(events)
                                .extracting("eventType")
                                .containsExactly("CREATE", "SUBMIT", "TA_ACCEPT", "DELAY");

                // 份额保持冻结（延期期间占用）
                var position = positionRepository
                                .findByCustomerNoAndProductCode("CUST2026000005", "P-PR-03").orElseThrow();
                org.assertj.core.api.Assertions.assertThat(position.getFrozenShares())
                                .isEqualByComparingTo("30000.00"); // 种子在途 10000 + 本单 20000

                // 模拟调度器到期触发（T+1 语义，演示环境为延迟秒数）
                orderService.confirmRedeemAndSettle(order, order.getTaSerialNo());

                org.assertj.core.api.Assertions.assertThat(order.getStatus()).isEqualTo("REDEEMED");
                org.assertj.core.api.Assertions.assertThat(positionRepository
                                .findByCustomerNoAndProductCode("CUST2026000005", "P-PR-03").orElseThrow()
                                .getShares())
                                .isEqualByComparingTo("85300.00");
                org.assertj.core.api.Assertions.assertThat(positionRepository
                                .findByCustomerNoAndProductCode("CUST2026000005", "P-PR-03").orElseThrow()
                                .getFrozenShares())
                                .isEqualByComparingTo("10000.00"); // 种子在途单的冻结保留
        }

        @Test
        void 撤单_申购在途订单解冻资金并释放额度() throws Exception {
                MockHttpSession session = login("13800000004");
                BigDecimal quotaBefore = productRepository.findByProductCode("P-CS-02").orElseThrow()
                                .getUsedQuota();

                // 种子在途单：c4 P-CS-02 代销申购 TA_ACCEPTED，冻结 50000
                MvcResult result = cancel(session, "ORD20260915001");
                expect(result, status().isOk(), jsonPath("$.status").value("CANCELLED"));

                var order = orderRepository.findByOrderNo("ORD20260915001").orElseThrow();
                org.assertj.core.api.Assertions.assertThat(order.getStatus()).isEqualTo("CANCELLED");
                // 资金解冻：可用 +50000、冻结 50000 → 0
                var capital = capitalAccountRepository.findByCustomerNo("CUST2026000004").orElseThrow();
                org.assertj.core.api.Assertions.assertThat(capital.getAvailableBalance())
                                .isEqualByComparingTo("919100.00");
                org.assertj.core.api.Assertions.assertThat(capital.getFrozenBalance())
                                .isEqualByComparingTo("0.00");
                // 额度释放
                org.assertj.core.api.Assertions.assertThat(
                                productRepository.findByProductCode("P-CS-02").orElseThrow().getUsedQuota())
                                .isEqualByComparingTo(quotaBefore.subtract(new BigDecimal("50000")));
                // 事件轨迹留痕
                var events = orderEventRepository.findByOrderNoOrderByCreatedAtAsc("ORD20260915001");
                org.assertj.core.api.Assertions.assertThat(events.get(events.size() - 1).getEventType())
                                .isEqualTo("CANCEL");
        }

        @Test
        void 撤单_赎回在途订单解冻份额() throws Exception {
                MockHttpSession session = login("13800000005");
                // 种子在途单：c5 P-PR-03 赎回 SUBMITTED，冻结 10000 份
                MvcResult result = cancel(session, "ORD20260915002");
                expect(result, status().isOk(), jsonPath("$.status").value("CANCELLED"));

                var position = positionRepository
                                .findByCustomerNoAndProductCode("CUST2026000005", "P-PR-03").orElseThrow();
                org.assertj.core.api.Assertions.assertThat(position.getShares())
                                .isEqualByComparingTo("105300.00"); // 份额未扣减
                org.assertj.core.api.Assertions.assertThat(position.getFrozenShares())
                                .isEqualByComparingTo("0.00"); // 冻结释放
        }

        @Test
        void 终态订单不可撤() throws Exception {
                MockHttpSession session = login("13800000004");
                MvcResult result = cancel(session, "ORD20260701001");
                expect(result, status().isBadRequest(), jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
        }

        @Test
        void 不可撤销他人订单() throws Exception {
                MockHttpSession session = login("13800000005");
                // c4 的在途单
                MvcResult result = cancel(session, "ORD20260915001");
                expect(result, status().isNotFound(), jsonPath("$.code").value("ORDER_NOT_FOUND"));
        }

        @Test
        void 赎回可用份额不足拦截() throws Exception {
                MockHttpSession session = login("13800000005");
                // P-PR-03 可用 = 105300 − 10000 冻结 = 95300
                MvcResult result = redeem(session, "P-PR-03", "100000", "T-RD-INSUFF-1");
                expect(result, status().isBadRequest(),
                                jsonPath("$.code").value("INSUFFICIENT_SHARES"));
                org.assertj.core.api.Assertions.assertThat(
                                orderRepository.findByClientRequestId("T-RD-INSUFF-1").orElseThrow().getStatus())
                                .isEqualTo("FAILED");
        }

        @Test
        void 代销赎回异步回调结算() throws Exception {
                MockHttpSession session = login("13800000004");
                // P-CS-01 总份额 30000（c4 独有），阈值 10% = 3000；赎 2000 非巨额
                MvcResult result = redeem(session, "P-CS-01", "2000", "T-RD-CS-1");
                expect(result, status().isOk(), jsonPath("$.status").value("TA_ACCEPTED"),
                                jsonPath("$.taSerialNo").isNotEmpty());

                var order = orderRepository.findByClientRequestId("T-RD-CS-1").orElseThrow();
                // 模拟外部 TA 异步确认回调
                externalTaConfirmService.handle(new TaCallback("TEST-EXTA-RD-001",
                                order.getOrderNo(), "CONFIRMED", new BigDecimal("2000.00"), "测试赎回回调"));

                org.assertj.core.api.Assertions.assertThat(order.getStatus()).isEqualTo("REDEEMED");
                // 持仓 30000 − 2000 = 28000
                org.assertj.core.api.Assertions.assertThat(positionRepository
                                .findByCustomerNoAndProductCode("CUST2026000004", "P-CS-01").orElseThrow()
                                .getShares())
                                .isEqualByComparingTo("28000.00");
                // 回款流水存在
                BigDecimal nav = latestNav("P-CS-01");
                BigDecimal gross = new BigDecimal("2000").multiply(nav).setScale(2, RoundingMode.HALF_UP);
                BigDecimal net = gross.subtract(
                                gross.multiply(new BigDecimal("0.0050")).setScale(2, RoundingMode.HALF_UP));
                org.assertj.core.api.Assertions.assertThat(order.getAmount()).isEqualByComparingTo(gross);
                org.assertj.core.api.Assertions.assertThat(order.getFee())
                                .isEqualByComparingTo(gross.subtract(net));
        }

        @Test
        void 赎回幂等_同一clientRequestId返回原订单() throws Exception {
                MockHttpSession session = login("13800000005");
                redeem(session, "P-PR-03", "5000", "T-RD-IDEM-1");
                long countBefore = orderRepository.count();
                MvcResult second = redeem(session, "P-PR-03", "5000", "T-RD-IDEM-1");
                expect(second, status().isOk());
                org.assertj.core.api.Assertions.assertThat(orderRepository.count()).isEqualTo(countBefore);
        }

        @Test
        void 撤单时段外拦截() throws Exception {
                em.createQuery("update Product p set p.tradeStartTime = :st, p.tradeEndTime = :et "
                                + "where p.productCode = 'P-PR-03'")
                                .setParameter("st", LocalTime.of(0, 0))
                                .setParameter("et", LocalTime.of(1, 0))
                                .executeUpdate();
                MockHttpSession session = login("13800000005");
                MvcResult result = cancel(session, "ORD20260915002");
                expect(result, status().isBadRequest(), jsonPath("$.code").value("TRADE_WINDOW_CLOSED"));
        }
}

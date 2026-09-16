package com.cib.ai.test.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cib.ai.test.model.InvestPlanResponse;
import com.cib.ai.test.repository.InvestPlanRepository;
import com.cib.ai.test.service.InvestPlanService;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 投资计划与分红方式集成测试（spec 用户故事 22/23，功能 12）：
 * 预约申购到期触发自动下单（复用 purchase 校验链 + 幂等）、定投周期推进、
 * 触发失败预约作废 EXPIRED、计划取消、分红方式设置走 TA 报文（自营/代销路由）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InvestPlanDividendIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private InvestPlanService investPlanService;

        @Autowired
        private InvestPlanRepository investPlanRepository;

        private MockHttpSession login(String mobile) throws Exception {
                MvcResult result = mockMvc.perform(post("/api/customer/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mobile\":\"%s\",\"password\":\"Passw0rd!\",\"channelCode\":\"PC_WEB\"}"
                                                .formatted(mobile)))
                                .andExpect(status().isOk())
                                .andReturn();
                return (MockHttpSession) result.getRequest().getSession();
        }

        private String createPlan(MockHttpSession session, String body) throws Exception {
                return mockMvc.perform(post("/api/customer/plans").session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();
        }

        @Test
        void 预约申购_今日到期触发自动下单_计划FINISHED() throws Exception {
                MockHttpSession session = login("13800000001");
                String today = LocalDate.now().toString();
                String planNo = com.jayway.jsonpath.JsonPath.read(
                                createPlan(session, """
                                                {"productCode":"P-PR-01","planType":"RESERVE",
                                                 "amount":1000.00,"triggerDate":"%s"}
                                                """.formatted(today)), "$.planNo");

                // 手动触发调度逻辑（等价 @Scheduled 到期扫描）
                investPlanService.triggerDuePlans();

                // 计划 FINISHED + 订单产生（c1 自营 P-PR-01 全校验通过 → SETTLED）
                mockMvc.perform(get("/api/customer/plans").session(session))
                                .andExpect(jsonPath("$.plans[0].planNo").value(planNo))
                                .andExpect(jsonPath("$.plans[0].status").value("FINISHED"));
                mockMvc.perform(get("/api/customer/orders").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].productCode").value("P-PR-01"))
                                .andExpect(jsonPath("$[0].status").value("SETTLED"))
                                .andExpect(jsonPath("$[0].amount").value(1000.00));

                // 幂等：重复触发不重复下单（clientRequestId = 计划号+触发日）
                investPlanService.triggerDuePlans();
                mockMvc.perform(get("/api/customer/orders").session(session))
                                .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        void 定投_触发后推进下一期_状态保持ACTIVE() throws Exception {
                MockHttpSession session = login("13800000001");
                String today = LocalDate.now().toString();
                String planNo = com.jayway.jsonpath.JsonPath.read(
                                createPlan(session, """
                                                {"productCode":"P-PR-01","planType":"REGULAR_INVEST",
                                                 "amount":500.00,"triggerDate":"%s","periodType":"WEEK"}
                                                """.formatted(today)), "$.planNo");

                investPlanService.triggerDuePlans();

                // 订单产生 + 计划推进 7 天仍 ACTIVE
                mockMvc.perform(get("/api/customer/orders").session(session))
                                .andExpect(jsonPath("$[0].status").value("SETTLED"));
                mockMvc.perform(get("/api/customer/plans").session(session))
                                .andExpect(jsonPath("$.plans[0].status").value("ACTIVE"))
                                .andExpect(jsonPath("$.plans[0].nextTriggerDate")
                                                .value(LocalDate.now().plusWeeks(1).toString()));
                // 种子 c1 定投计划（next=10-01 未到期）不受影响
                org.assertj.core.api.Assertions.assertThat(
                                investPlanRepository.findByPlanNo("PLAN20260601001").orElseThrow().getStatus())
                                .isEqualTo("ACTIVE");
        }

        @Test
        void 预约触发失败_适当性拦截_计划EXPIRED() throws Exception {
                // c1 买 P-PR-02 会被适当性拦截（等级不足）
                MockHttpSession session = login("13800000001");
                String today = LocalDate.now().toString();
                createPlan(session, """
                                {"productCode":"P-PR-02","planType":"RESERVE",
                                 "amount":20000.00,"triggerDate":"%s"}
                                """.formatted(today));

                investPlanService.triggerDuePlans();

                mockMvc.perform(get("/api/customer/plans").session(session))
                                .andExpect(jsonPath("$.plans[0].status").value("EXPIRED"));
                // 失败订单留痕
                mockMvc.perform(get("/api/customer/orders").session(session))
                                .andExpect(jsonPath("$[0].status").value("FAILED"));
        }

        @Test
        void 取消计划_ACTIVE转CANCELLED() throws Exception {
                MockHttpSession session = login("13800000001");
                String planNo = com.jayway.jsonpath.JsonPath.read(
                                createPlan(session, """
                                                {"productCode":"P-PR-01","planType":"RESERVE",
                                                 "amount":1000.00,"triggerDate":"2026-12-31"}
                                                """), "$.planNo");

                mockMvc.perform(delete("/api/customer/plans/{planNo}", planNo).session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("CANCELLED"));

                // 已取消计划不可再取消
                mockMvc.perform(delete("/api/customer/plans/{planNo}", planNo).session(session))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("PLAN_NOT_CANCELLABLE"));
        }

        @Test
        void 取消他人计划_404() throws Exception {
                MockHttpSession session = login("13800000004");
                mockMvc.perform(delete("/api/customer/plans/PLAN20260901001").session(session))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
        }

        @Test
        void 创建计划_日期与周期校验() throws Exception {
                MockHttpSession session = login("13800000001");
                // 触发日期早于今日
                mockMvc.perform(post("/api/customer/plans").session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"productCode":"P-PR-01","planType":"RESERVE",
                                                 "amount":1000.00,"triggerDate":"2026-01-01"}
                                                """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("TRIGGER_DATE_INVALID"));
                // 定投缺周期
                mockMvc.perform(post("/api/customer/plans").session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"productCode":"P-PR-01","planType":"REGULAR_INVEST",
                                                 "amount":500.00,"triggerDate":"2026-12-31"}
                                                """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("PERIOD_TYPE_REQUIRED"));
        }

        @Test
        void 分红设置_自营走本行TA() throws Exception {
                MockHttpSession session = login("13800000001");
                mockMvc.perform(put("/api/customer/dividend-setting").session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productCode\":\"P-PR-01\",\"dividendType\":\"REINVEST\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.dividendType").value("REINVEST"))
                                .andExpect(jsonPath("$.productType").value("PROPRIETARY"))
                                .andExpect(jsonPath("$.taSerialNo").isNotEmpty());

                mockMvc.perform(get("/api/customer/dividend-settings").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].productCode").value("P-PR-01"))
                                .andExpect(jsonPath("$[0].dividendType").value("REINVEST"));
        }

        @Test
        void 分红设置_代销走外部TA_可覆盖更新() throws Exception {
                // c4 种子已有 P-CS-01 CASH，更新为 REINVEST
                MockHttpSession session = login("13800000004");
                mockMvc.perform(put("/api/customer/dividend-setting").session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productCode\":\"P-CS-01\",\"dividendType\":\"REINVEST\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.productType").value("CONSIGNMENT"))
                                .andExpect(jsonPath("$.dividendType").value("REINVEST"));

                mockMvc.perform(get("/api/customer/dividend-settings").session(session))
                                .andExpect(jsonPath("$.length()").value(1))
                                .andExpect(jsonPath("$[0].dividendType").value("REINVEST"));
        }

        @Test
        void 分红设置_非法类型400_产品不存在404() throws Exception {
                MockHttpSession session = login("13800000001");
                mockMvc.perform(put("/api/customer/dividend-setting").session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productCode\":\"P-PR-01\",\"dividendType\":\"CASH1\"}"))
                                .andExpect(status().isBadRequest());
                mockMvc.perform(put("/api/customer/dividend-setting").session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productCode\":\"P-XX-99\",\"dividendType\":\"CASH\"}"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        }

        @Test
        void 未登录_401() throws Exception {
                mockMvc.perform(get("/api/customer/plans"))
                                .andExpect(status().isUnauthorized());
                mockMvc.perform(put("/api/customer/dividend-setting")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productCode\":\"P-PR-01\",\"dividendType\":\"CASH\"}"))
                                .andExpect(status().isUnauthorized());
        }
}

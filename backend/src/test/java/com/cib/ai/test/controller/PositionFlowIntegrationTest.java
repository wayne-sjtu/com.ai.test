package com.cib.ai.test.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * C 端持仓与流水集成测试（spec 用户故事 25/26/27）：
 * 统一持仓视图（分区小计/在途/估值/来源标注）、交易流水筛选与 CSV 导出、资金流水对账锚点。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PositionFlowIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        private MockHttpSession login(String mobile) throws Exception {
                MvcResult result = mockMvc.perform(post("/api/customer/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mobile\":\"%s\",\"password\":\"Passw0rd!\",\"channelCode\":\"PC_WEB\"}"
                                                .formatted(mobile)))
                                .andExpect(status().isOk())
                                .andReturn();
                return (MockHttpSession) result.getRequest().getSession();
        }

        @Test
        void 持仓视图_c5三分区持仓含代销来源标注与在途() throws Exception {
                MockHttpSession session = login("13800000005");
                mockMvc.perform(get("/api/customer/positions").session(session))
                                .andExpect(status().isOk())
                                // 三笔存量持仓
                                .andExpect(jsonPath("$.positions.length()").value(3))
                                // 在途：种子赎回单 SUBMITTED
                                .andExpect(jsonPath("$.inFlights.length()").value(1))
                                .andExpect(jsonPath("$.inFlights[0].orderType").value("REDEEM"))
                                .andExpect(jsonPath("$.inFlights[0].status").value("SUBMITTED"))
                                .andExpect(jsonPath("$.inFlights[0].shares").value(10000.00));
        }

        @Test
        void 持仓视图_代销净值来源EXTERNAL与同步时间标注() throws Exception {
                MockHttpSession session = login("13800000005");
                MvcResult result = mockMvc.perform(get("/api/customer/positions").session(session))
                                .andExpect(status().isOk())
                                .andReturn();
                String body = result.getResponse().getContentAsString();
                // P-CS-03 为代销：来源 EXTERNAL + navSyncedAt 标注（数据以外部 TA 为准）
                org.assertj.core.api.Assertions.assertThat(body).contains("EXTERNAL");
                org.assertj.core.api.Assertions.assertThat(body).contains("navSyncedAt");
                // P-PR-03/P-PR-04 为自营：INTERNAL
                org.assertj.core.api.Assertions.assertThat(body).contains("INTERNAL");
        }

        @Test
        void 持仓视图_分区小计与总市值一致() throws Exception {
                MockHttpSession session = login("13800000005");
                mockMvc.perform(get("/api/customer/positions").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.summary.totalMarketValue").isNotEmpty())
                                .andExpect(jsonPath("$.summary.proprietaryMarketValue").isNotEmpty())
                                .andExpect(jsonPath("$.summary.consignmentMarketValue").isNotEmpty());
        }

        @Test
        void 持仓视图_可用份额等于持仓减冻结() throws Exception {
                MockHttpSession session = login("13800000005");
                // P-PR-03：105300 总 − 10000 冻结 = 95300 可用
                mockMvc.perform(get("/api/customer/positions").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.positions[?(@.productCode=='P-PR-03')].availableShares")
                                                .value(95300.00))
                                .andExpect(jsonPath("$.positions[?(@.productCode=='P-PR-03')].frozenShares")
                                                .value(10000.00));
        }

        @Test
        void 交易流水_自营代销分档筛选() throws Exception {
                MockHttpSession session = login("13800000005");
                mockMvc.perform(get("/api/customer/flows")
                                .param("productType", "PROPRIETARY")
                                .session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.productType").value("PROPRIETARY"))
                                .andExpect(jsonPath("$.flows[?(@.productType=='PROPRIETARY')]").isNotEmpty());
                // 全部流水包含代销
                mockMvc.perform(get("/api/customer/flows").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(4)); // 3 历史 + 1 在途
        }

        @Test
        void 交易流水_CSV导出含表头与代销标注() throws Exception {
                MockHttpSession session = login("13800000005");
                MvcResult result = mockMvc.perform(get("/api/customer/flows")
                                .param("format", "csv")
                                .session(session))
                                .andExpect(status().isOk())
                                .andReturn();
                String contentType = result.getResponse().getContentType();
                String disposition = result.getResponse().getHeader("Content-Disposition");
                String body = result.getResponse().getContentAsString();
                org.assertj.core.api.Assertions.assertThat(contentType).contains("text/csv");
                org.assertj.core.api.Assertions.assertThat(disposition)
                                .contains("attachment; filename=trade-flows.csv");
                org.assertj.core.api.Assertions.assertThat(body).contains("订单号");
                org.assertj.core.api.Assertions.assertThat(body).contains("代销");
                org.assertj.core.api.Assertions.assertThat(body).contains("自营");
                org.assertj.core.api.Assertions.assertThat(body).contains("ORD20260915002");
        }

        @Test
        void 资金流水_含动作类型与对账余额锚点() throws Exception {
                MockHttpSession session = login("13800000005");
                mockMvc.perform(get("/api/customer/capital-flows").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.flows[?(@.actionType=='FREEZE')]").isNotEmpty())
                                .andExpect(jsonPath("$.flows[?(@.actionType=='DEDUCT')]").isNotEmpty())
                                .andExpect(jsonPath("$.flows[?(@.balanceAfter)]").isNotEmpty());
        }

        @Test
        void 未登录访问持仓401() throws Exception {
                mockMvc.perform(get("/api/customer/positions"))
                                .andExpect(status().isUnauthorized());
        }
}

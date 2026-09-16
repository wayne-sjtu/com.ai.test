package com.ai.test.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.test.repository.MessageRepository;
import com.ai.test.repository.ProductNavRepository;
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
 * 管理端订单域集成测试（spec 用户故事 32/33/34）：
 * 统一订单列表（筛选 + 路由链路字段）、事件轨迹、自营净值发布三校验 + 持有人消息、
 * 健康矩阵、客户 Session 越权 403。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminOrderIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ProductNavRepository productNavRepository;

        @Autowired
        private MessageRepository messageRepository;

        private MockHttpSession loginAdmin() throws Exception {
                MvcResult result = mockMvc.perform(post("/api/admin/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"admin_op\",\"password\":\"Admin123!\"}"))
                                .andExpect(status().isOk())
                                .andReturn();
                return (MockHttpSession) result.getRequest().getSession();
        }

        private MockHttpSession loginCustomer() throws Exception {
                MvcResult result = mockMvc.perform(post("/api/customer/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mobile\":\"13800000004\",\"password\":\"Passw0rd!\",\"channelCode\":\"PC_WEB\"}"))
                                .andExpect(status().isOk())
                                .andReturn();
                return (MockHttpSession) result.getRequest().getSession();
        }

        @Test
        void 统一订单列表_含客户号与路由流水() throws Exception {
                MockHttpSession session = loginAdmin();
                mockMvc.perform(get("/api/admin/orders").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[?(@.customerNo)]").isNotEmpty())
                                .andExpect(jsonPath("$[?(@.productType)]").isNotEmpty());
        }

        @Test
        void 订单筛选_代销在途单() throws Exception {
                MockHttpSession session = loginAdmin();
                mockMvc.perform(get("/api/admin/orders")
                                .param("productType", "CONSIGNMENT")
                                .param("status", "TA_ACCEPTED")
                                .session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1))
                                .andExpect(jsonPath("$[0].orderNo").value("ORD20260915001"))
                                .andExpect(jsonPath("$[0].customerNo").value("CUST2026000004"))
                                .andExpect(jsonPath("$[0].taSerialNo").isNotEmpty());
        }

        @Test
        void 订单事件轨迹_在途单显示TA受理链路() throws Exception {
                MockHttpSession session = loginAdmin();
                mockMvc.perform(get("/api/admin/orders/ORD20260915001/events").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(3))
                                .andExpect(jsonPath("$[0].eventType").value("CREATE"))
                                .andExpect(jsonPath("$[1].eventType").value("SUBMIT"))
                                .andExpect(jsonPath("$[2].eventType").value("TA_ACCEPT"))
                                .andExpect(jsonPath("$[2].taTag").value("EXTA"))
                                .andExpect(jsonPath("$[2].taSerialNo").isNotEmpty());
        }

        @Test
        void 订单轨迹_订单不存在404() throws Exception {
                MockHttpSession session = loginAdmin();
                mockMvc.perform(get("/api/admin/orders/ORD-XXX-404/events").session(session))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        }

        @Test
        void 净值发布_自营成功并推送持有人消息() throws Exception {
                MockHttpSession session = loginAdmin();
                long navCountBefore = productNavRepository.count();
                long msgCountBefore = messageRepository.count();
                // P-PR-01 持有人：c4
                mockMvc.perform(post("/api/admin/nav/publish")
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productCode\":\"P-PR-01\",\"navDate\":\"2026-09-16\",\"nav\":1.0325}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.source").value("INTERNAL"))
                                .andExpect(jsonPath("$.nav").value(1.0325));
                org.assertj.core.api.Assertions.assertThat(productNavRepository.count())
                                .isEqualTo(navCountBefore + 1);
                // 持有人 c4 收到净值消息
                org.assertj.core.api.Assertions.assertThat(messageRepository.count())
                                .isEqualTo(msgCountBefore + 1);
                var msg = messageRepository
                                .findByCustomerNoOrderByCreatedAtDesc("CUST2026000004").get(0);
                org.assertj.core.api.Assertions.assertThat(msg.getMsgType()).isEqualTo("NAV");
                org.assertj.core.api.Assertions.assertThat(msg.getTitle()).contains("现金宝1号");
        }

        @Test
        void 净值发布_代销产品禁止() throws Exception {
                MockHttpSession session = loginAdmin();
                mockMvc.perform(post("/api/admin/nav/publish")
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productCode\":\"P-CS-01\",\"navDate\":\"2026-09-16\",\"nav\":1.0}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("NAV_CONSIGNMENT_FORBIDDEN"));
        }

        @Test
        void 净值发布_重复日期禁止() throws Exception {
                MockHttpSession session = loginAdmin();
                // 种子已含 P-PR-01 的 2026-09-15 净值
                mockMvc.perform(post("/api/admin/nav/publish")
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productCode\":\"P-PR-01\",\"navDate\":\"2026-09-15\",\"nav\":1.0}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("NAV_ALREADY_EXISTS"));
        }

        @Test
        void 健康矩阵_三组件UP且外部TA带故障模式() throws Exception {
                MockHttpSession session = loginAdmin();
                mockMvc.perform(get("/api/admin/health/matrix").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.overall").value("UP"))
                                .andExpect(jsonPath("$.components.length()").value(3))
                                .andExpect(jsonPath("$.components[0].name").value("DB"))
                                .andExpect(jsonPath("$.components[1].name").value("INTERNAL_TA"))
                                .andExpect(jsonPath("$.components[2].name").value("EXTERNAL_TA"))
                                .andExpect(jsonPath("$.components[2].faultMode").value("NORMAL"));
        }

        @Test
        void 客户Session访问管理端订单403() throws Exception {
                MockHttpSession session = loginCustomer();
                mockMvc.perform(get("/api/admin/orders").session(session))
                                .andExpect(status().isForbidden());
        }
}

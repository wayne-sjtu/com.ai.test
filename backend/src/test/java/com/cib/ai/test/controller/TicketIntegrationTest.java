package com.cib.ai.test.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * 工单域集成测试（spec 用户故事 29/31）：
 * C 端提交/查询（代销投诉外部同步 PENDING）、管理端受理/关闭
 * （代销投诉 SYNCED 闭环 + TICKET_PROGRESS 消息推送）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TicketIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        private MockHttpSession loginCustomer(String mobile) throws Exception {
                MvcResult result = mockMvc.perform(post("/api/customer/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mobile\":\"%s\",\"password\":\"Passw0rd!\",\"channelCode\":\"PC_WEB\"}"
                                                .formatted(mobile)))
                                .andExpect(status().isOk())
                                .andReturn();
                return (MockHttpSession) result.getRequest().getSession();
        }

        private MockHttpSession loginAdmin() throws Exception {
                MvcResult result = mockMvc.perform(post("/api/admin/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"admin_cs\",\"password\":\"Admin123!\"}"))
                                .andExpect(status().isOk())
                                .andReturn();
                return (MockHttpSession) result.getRequest().getSession();
        }

        @Test
        void 提交代销投诉_外部同步PENDING() throws Exception {
                MockHttpSession session = loginCustomer("13800000004");
                mockMvc.perform(post("/api/customer/tickets").session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"ticketType":"COMPLAINT","productCode":"P-CS-01",
                                                 "content":"代销产品净值披露不及时，请核实。"}
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("OPEN"))
                                .andExpect(jsonPath("$.productType").value("CONSIGNMENT"))
                                .andExpect(jsonPath("$.externalSyncStatus").value("PENDING"))
                                .andExpect(jsonPath("$.ticketNo").isNotEmpty());
        }

        @Test
        void 提交自营投诉_内部流转无外部同步() throws Exception {
                MockHttpSession session = loginCustomer("13800000001");
                mockMvc.perform(post("/api/customer/tickets").session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"ticketType":"COMPLAINT","productCode":"P-PR-01",
                                                 "content":"自营产品申购确认时间过长。"}
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.productType").value("PROPRIETARY"))
                                .andExpect(jsonPath("$.externalSyncStatus").doesNotExist());
        }

        @Test
        void 提交咨询_不带产品() throws Exception {
                MockHttpSession session = loginCustomer("13800000001");
                mockMvc.perform(post("/api/customer/tickets").session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"ticketType":"CONSULT","content":"请问定投计划如何修改扣款日？"}
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.ticketType").value("CONSULT"))
                                .andExpect(jsonPath("$.status").value("OPEN"));
        }

        @Test
        void 提交关联产品不存在_404() throws Exception {
                MockHttpSession session = loginCustomer("13800000001");
                mockMvc.perform(post("/api/customer/tickets").session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"ticketType":"COMPLAINT","productCode":"P-XX-99",
                                                 "content":"产品不存在测试"}
                                                """))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        }

        @Test
        void 本人工单列表_含种子与产品类型标注() throws Exception {
                // c4 种子 1 张代销投诉（PROCESSING/PENDING）
                MockHttpSession session = loginCustomer("13800000004");
                mockMvc.perform(get("/api/customer/tickets").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(1))
                                .andExpect(jsonPath("$.tickets[0].ticketNo").value("TK20260910001"))
                                .andExpect(jsonPath("$.tickets[0].productType").value("CONSIGNMENT"))
                                .andExpect(jsonPath("$.tickets[0].externalSyncStatus").value("PENDING"));
        }

        @Test
        void 管理端列表_状态筛选() throws Exception {
                MockHttpSession admin = loginAdmin();
                mockMvc.perform(get("/api/admin/tickets")
                                .param("status", "PROCESSING").session(admin))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].ticketNo").value("TK20260910001"));
        }

        @Test
        void 管理端受理_状态PROCESSING并推送进度消息() throws Exception {
                MockHttpSession admin = loginAdmin();
                mockMvc.perform(put("/api/admin/tickets/TK20260910001/handle").session(admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"action\":\"ACCEPT\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("PROCESSING"))
                                .andExpect(jsonPath("$.handler").value("admin_cs"));

                // 客户收到 TICKET_PROGRESS 站内消息
                MockHttpSession customer = loginCustomer("13800000004");
                mockMvc.perform(get("/api/customer/messages")
                                .param("msgType", "TICKET_PROGRESS").session(customer))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(1))
                                .andExpect(jsonPath("$.messages[0].title").value("工单进度更新：TK20260910001"));
        }

        @Test
        void 管理端关闭代销投诉_答复必填_SYNCED闭环_消息含答复() throws Exception {
                MockHttpSession admin = loginAdmin();
                // 受理
                mockMvc.perform(put("/api/admin/tickets/TK20260910001/handle").session(admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"action\":\"ACCEPT\"}"))
                                .andExpect(status().isOk());
                // 关闭（代销投诉 → SYNCED）
                mockMvc.perform(put("/api/admin/tickets/TK20260910001/handle").session(admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"action\":\"CLOSE\",\"reply\":\"已与发行机构核实，赎回款T+2到账。\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("CLOSED"))
                                .andExpect(jsonPath("$.externalSyncStatus").value("SYNCED"))
                                .andExpect(jsonPath("$.reply").value("已与发行机构核实，赎回款T+2到账。"));

                // 客户消息含答复内容
                MockHttpSession customer = loginCustomer("13800000004");
                mockMvc.perform(get("/api/customer/messages")
                                .param("msgType", "TICKET_PROGRESS").session(customer))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(2))
                                .andExpect(jsonPath("$.messages[0].content")
                                                .value(org.hamcrest.Matchers.containsString("T+2到账")));
        }

        @Test
        void 关闭缺答复_400() throws Exception {
                MockHttpSession admin = loginAdmin();
                mockMvc.perform(put("/api/admin/tickets/TK20260910001/handle").session(admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"action\":\"CLOSE\"}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("REPLY_REQUIRED"));
        }

        @Test
        void 重复关闭_400() throws Exception {
                MockHttpSession admin = loginAdmin();
                mockMvc.perform(put("/api/admin/tickets/TK20260910001/handle").session(admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"action\":\"CLOSE\",\"reply\":\"处理完毕。\"}"))
                                .andExpect(status().isOk());
                mockMvc.perform(put("/api/admin/tickets/TK20260910001/handle").session(admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"action\":\"ACCEPT\"}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("TICKET_ALREADY_CLOSED"));
        }

        @Test
        void 工单不存在_404() throws Exception {
                MockHttpSession admin = loginAdmin();
                mockMvc.perform(put("/api/admin/tickets/TK-NOT-EXIST/handle").session(admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"action\":\"ACCEPT\"}"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
        }

        @Test
        void 客户身份访问管理端工单_拒绝() throws Exception {
                MockHttpSession customer = loginCustomer("13800000001");
                mockMvc.perform(get("/api/admin/tickets").session(customer))
                                .andExpect(status().isForbidden());
        }

        @Test
        void 未登录_401() throws Exception {
                mockMvc.perform(get("/api/customer/tickets"))
                                .andExpect(status().isUnauthorized());
        }
}

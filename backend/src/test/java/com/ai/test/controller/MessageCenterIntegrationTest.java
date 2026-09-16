package com.ai.test.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.test.repository.MessageRepository;
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
 * C 端消息中心集成测试（spec 用户故事 28）：
 * 站内信列表（类型筛选/只看未读/未读数）、单条已读（越权 404）、全部已读。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MessageCenterIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private MessageRepository messageRepository;

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
        void 消息列表_含总数与未读数() throws Exception {
                // c4 种子 2 条 DEAL：1 已读 + 1 未读
                MockHttpSession session = login("13800000004");
                mockMvc.perform(get("/api/customer/messages").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(2))
                                .andExpect(jsonPath("$.unreadCount").value(1))
                                .andExpect(jsonPath("$.messages[0].msgType").value("DEAL"))
                                .andExpect(jsonPath("$.messages[0].readFlag").exists())
                                .andExpect(jsonPath("$.messages[0].id").exists());
        }

        @Test
        void 消息列表_msgType筛选与只看未读() throws Exception {
                MockHttpSession session = login("13800000004");
                // msgType=DEAL：全部 2 条
                mockMvc.perform(get("/api/customer/messages")
                                .param("msgType", "DEAL").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(2));
                // unread=true：仅未读 1 条
                mockMvc.perform(get("/api/customer/messages")
                                .param("unread", "true").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(1))
                                .andExpect(jsonPath("$.messages[0].readFlag").value(false));
        }

        @Test
        void 单条已读_未读清零() throws Exception {
                MockHttpSession session = login("13800000004");
                Long unreadId = messageRepository
                                .findByCustomerNoOrderByCreatedAtDesc("CUST2026000004").stream()
                                .filter(m -> !Boolean.TRUE.equals(m.getReadFlag()))
                                .findFirst().orElseThrow().getId();
                mockMvc.perform(post("/api/customer/messages/{id}/read", unreadId).session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(unreadId))
                                .andExpect(jsonPath("$.readFlag").value(true));
                mockMvc.perform(get("/api/customer/messages").session(session))
                                .andExpect(jsonPath("$.unreadCount").value(0));
        }

        @Test
        void 单条已读_他人消息404() throws Exception {
                // c4 尝试标记 c1 的消息 → 统一 404，不泄露存在性
                MockHttpSession session = login("13800000004");
                Long c1MessageId = messageRepository
                                .findByCustomerNoOrderByCreatedAtDesc("CUST2026000001").stream()
                                .findFirst().orElseThrow().getId();
                mockMvc.perform(post("/api/customer/messages/{id}/read", c1MessageId).session(session))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("MESSAGE_NOT_FOUND"));
        }

        @Test
        void 全部已读_返回标记条数() throws Exception {
                // c6 种子 1 条 EXPIRY 未读
                MockHttpSession session = login("13800000006");
                mockMvc.perform(post("/api/customer/messages/read-all").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.markedRead").value(1));
                mockMvc.perform(get("/api/customer/messages").session(session))
                                .andExpect(jsonPath("$.unreadCount").value(0))
                                .andExpect(jsonPath("$.total").value(1))
                                .andExpect(jsonPath("$.messages[0].readFlag").value(true));
        }

        @Test
        void 未登录_401() throws Exception {
                mockMvc.perform(get("/api/customer/messages"))
                                .andExpect(status().isUnauthorized());
        }
}

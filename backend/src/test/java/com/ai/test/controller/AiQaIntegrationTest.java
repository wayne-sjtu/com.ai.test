package com.ai.test.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.test.llm.InProcessLlmClient;
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
 * AI 投教问答集成测试（spec 用户故事 30，功能 17）：
 * LLM 优先、FAQ 关键词降级（LLM 故障开关演示）、敏感问题转人工（自动工单保留记录）、
 * 未命中建议转人工；所有回答带来源提示 + 风险声明。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiQaIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private InProcessLlmClient llmClient;

        private MockHttpSession login(String mobile) throws Exception {
                MvcResult result = mockMvc.perform(post("/api/customer/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mobile\":\"%s\",\"password\":\"Passw0rd!\",\"channelCode\":\"PC_WEB\"}"
                                                .formatted(mobile)))
                                .andExpect(status().isOk())
                                .andReturn();
                return (MockHttpSession) result.getRequest().getSession();
        }

        private MvcResult ask(MockHttpSession session, String question) throws Exception {
                return mockMvc.perform(post("/api/customer/ai-qa").session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"question\":\"%s\"}".formatted(question)))
                                .andExpect(status().isOk())
                                .andReturn();
        }

        @Test
        void LLM优先_主题命中返回回答带来源与风险声明() throws Exception {
                MockHttpSession session = login("13800000001");
                mockMvc.perform(post("/api/customer/ai-qa").session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"question\":\"请问什么是单位净值？\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.source").value("LLM"))
                                .andExpect(jsonPath("$.answer").isNotEmpty())
                                .andExpect(jsonPath("$.needHuman").value(false))
                                // 风险声明强制附带
                                .andExpect(jsonPath("$.riskDisclaimer").isNotEmpty());
        }

        @Test
        void FAQ降级_LLM不可用时关键词命中() throws Exception {
                // 模拟 LLM 服务不可用（降级路径演示）
                llmClient.setAvailable(false);
                try {
                        MockHttpSession session = login("13800000001");
                        mockMvc.perform(post("/api/customer/ai-qa").session(session)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"question\":\"风险测评的有效期是多久？\"}"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.source").value("FAQ"))
                                        .andExpect(jsonPath("$.faqQuestion").value("风险测评的有效期是多久？"))
                                        .andExpect(jsonPath("$.answer").isNotEmpty())
                                        .andExpect(jsonPath("$.riskDisclaimer").isNotEmpty());
                } finally {
                        llmClient.setAvailable(true);
                }
        }

        @Test
        void 均未命中_建议转人工() throws Exception {
                MockHttpSession session = login("13800000001");
                mockMvc.perform(post("/api/customer/ai-qa").session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"question\":\"今天天气怎么样？\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.source").value("NONE"))
                                .andExpect(jsonPath("$.needHuman").value(true))
                                .andExpect(jsonPath("$.riskDisclaimer").isNotEmpty());
        }

        @Test
        void 敏感问题_转人工并自动创建工单保留记录() throws Exception {
                MockHttpSession session = login("13800000001");
                String body = mockMvc.perform(post("/api/customer/ai-qa").session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"question\":\"我买的理财产品亏损了怎么办，要求赔偿！\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.source").value("HUMAN"))
                                .andExpect(jsonPath("$.needHuman").value(true))
                                .andExpect(jsonPath("$.ticketNo").isNotEmpty())
                                .andReturn().getResponse().getContentAsString();

                // 工单内容保留原始问答记录（c1 已有 1 张种子工单，新工单置顶）
                String ticketNo = com.jayway.jsonpath.JsonPath.read(body, "$.ticketNo");
                mockMvc.perform(get("/api/customer/tickets").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(2))
                                .andExpect(jsonPath("$.tickets[0].ticketNo").value(ticketNo))
                                .andExpect(jsonPath("$.tickets[0].status").value("OPEN"))
                                .andExpect(jsonPath("$.tickets[0].content")
                                                .value(org.hamcrest.Matchers.containsString("AI 投教转人工")));
        }

        @Test
        void 空问题_400() throws Exception {
                MockHttpSession session = login("13800000001");
                mockMvc.perform(post("/api/customer/ai-qa").session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"question\":\"\"}"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void 未登录_401() throws Exception {
                mockMvc.perform(post("/api/customer/ai-qa")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"question\":\"什么是净值？\"}"))
                                .andExpect(status().isUnauthorized());
        }
}

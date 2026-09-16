package com.ai.test.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 认证域集成测试（ADR-0003/0004）：
 * 走真实 REST 端点验证外部可观察行为——登录/登出/会话查询/越权拒绝/渠道校验。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession loginCustomer(String mobile, String channelCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/customer/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"mobile":"%s","password":"Passw0rd!","channelCode":"%s"}
                        """.formatted(mobile, channelCode)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }

    private MockHttpSession loginAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"admin_op","password":"Admin123!"}
                        """))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }

    @Test
    void 客户登录成功返回客户信息并建立会话() throws Exception {
        mockMvc.perform(post("/api/customer/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"mobile":"13800000001","password":"Passw0rd!","channelCode":"PC_WEB"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerNo").value("CUST2026000001"))
                .andExpect(jsonPath("$.name").value("陈一"))
                .andExpect(jsonPath("$.realNameStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void 客户密码错误返回401() throws Exception {
        mockMvc.perform(post("/api/customer/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"mobile":"13800000001","password":"wrong","channelCode":"PC_WEB"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_FAILED"));
    }

    @Test
    void 手机号不存在与密码错误返回相同话术() throws Exception {
        mockMvc.perform(post("/api/customer/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"mobile":"13999999999","password":"whatever","channelCode":"PC_WEB"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("手机号或密码错误"));
    }

    @Test
    void 停用渠道登录被拒绝() throws Exception {
        mockMvc.perform(post("/api/customer/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"mobile":"13800000001","password":"Passw0rd!","channelCode":"MINI_PROGRAM"}
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHANNEL_SUSPENDED"));
    }

    @Test
    void 未实名未签约客户也可登录() throws Exception {
        mockMvc.perform(post("/api/customer/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"mobile":"13800000007","password":"Passw0rd!","channelCode":"PC_WEB"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realNameStatus").value("PENDING"))
                .andExpect(jsonPath("$.kycStatus").value("PENDING"));
    }

    @Test
    void 登录后可查询会话信息() throws Exception {
        MockHttpSession session = loginCustomer("13800000004", "PC_WEB");
        mockMvc.perform(get("/api/customer/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerNo").value("CUST2026000004"));
    }

    @Test
    void 未登录访问客户端点返回401() throws Exception {
        mockMvc.perform(get("/api/customer/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 客户会话访问管理端点返回403() throws Exception {
        MockHttpSession session = loginCustomer("13800000001", "PC_WEB");
        mockMvc.perform(get("/api/admin/me").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 管理端登录成功并查询会话信息() throws Exception {
        MockHttpSession session = loginAdmin();
        mockMvc.perform(get("/api/admin/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin_op"))
                .andExpect(jsonPath("$.role").value("OPERATION"));
    }

    @Test
    void 管理员会话访问客户端点返回403() throws Exception {
        MockHttpSession session = loginAdmin();
        mockMvc.perform(get("/api/customer/me").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 客户登出后会话失效() throws Exception {
        MockHttpSession session = loginCustomer("13800000001", "PC_WEB");
        mockMvc.perform(post("/api/customer/logout").session(session))
                .andExpect(status().isNoContent());
        // 登出后原会话失效，再访问需重新登录
        mockMvc.perform(get("/api/customer/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 登录入参缺失返回400() throws Exception {
        mockMvc.perform(post("/api/customer/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"mobile":"13800000001","channelCode":"PC_WEB"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAM_INVALID"));
    }
}

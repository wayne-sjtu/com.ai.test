package com.ai.test.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
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
 * 账户与准入域集成测试（spec 功能 4/5/6）：
 * 签约前置校验、解约三重校验与原因列表、测评 append-only 与过期标志、电子签收幂等。
 * 各用例使用不同种子客户，互不影响数据状态。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountAdmissionIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private EntityManager em;

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

        private long assessmentCount(String customerNo) {
                return em.createQuery(
                                "select count(r) from RiskAssessment r where r.customerNo = :no", Long.class)
                                .setParameter("no", customerNo)
                                .getSingleResult();
        }

        @Test
        void 未签约客户全链路_实名_签文件_签约_测评() throws Exception {
                MockHttpSession session = login("13800000007");
                long before = assessmentCount("CUST2026000007");

                // 初始：未签约 + 无测评
                mockMvc.perform(get("/api/customer/account").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("UNSIGNED"))
                                .andExpect(jsonPath("$.permissions").isEmpty());
                mockMvc.perform(get("/api/customer/assessment").session(session))
                                .andExpect(status().isNotFound());

                // 直接签约：实名/尽调未完成 + 四类文件未签（共 6 条原因）
                mockMvc.perform(post("/api/customer/account/sign").session(session))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("SIGN_PRECONDITION_FAILED"))
                                .andExpect(jsonPath("$.reasons.length()").value(6));

                // Mock 实名认证后：仅剩文件未签（4 条原因）
                mockMvc.perform(post("/api/customer/real-name/verify").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.realNameStatus").value("VERIFIED"))
                                .andExpect(jsonPath("$.kycStatus").value("PASSED"));
                mockMvc.perform(post("/api/customer/account/sign").session(session))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.reasons.length()").value(4));

                // 签署四类文件后签约成功：SIGNED + 双权限 OPEN
                for (String type : new String[] { "RIGHTS_NOTICE", "RISK_DISCLOSURE",
                                "PRODUCT_AGREEMENT", "CONSIGNMENT_DISCLOSURE" }) {
                        mockMvc.perform(post("/api/customer/documents/%s/sign".formatted(type)).session(session))
                                        .andExpect(status().isOk());
                }
                mockMvc.perform(post("/api/customer/account/sign").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("SIGNED"))
                                .andExpect(jsonPath("$.permissions.length()").value(2))
                                .andExpect(jsonPath("$.permissions[0].status").value("OPEN"));

                // 提交测评：C,D,D,D,C = 90 分 → C5（append-only，历史 +1）
                mockMvc.perform(post("/api/customer/assessment").session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"answers\":\"C,D,D,D,C\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.riskLevel").value("C5"))
                                .andExpect(jsonPath("$.score").value(90))
                                .andExpect(jsonPath("$.expired").value(false));
                org.assertj.core.api.Assertions.assertThat(assessmentCount("CUST2026000007")).isEqualTo(before + 1);
        }

        @Test
        void 已签约客户重复签约被拒绝() throws Exception {
                MockHttpSession session = login("13800000003");
                mockMvc.perform(post("/api/customer/account/sign").session(session))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("SIGN_PRECONDITION_FAILED"));
        }

        @Test
        void 持仓在途冻结客户解约被拒绝并返回全部原因() throws Exception {
                MockHttpSession session = login("13800000004");
                mockMvc.perform(post("/api/customer/account/terminate").session(session))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("TERMINATE_REJECTED"))
                                .andExpect(jsonPath("$.reasons.length()").value(3));
        }

        @Test
        void 持仓与在途客户解约拒绝两条原因() throws Exception {
                MockHttpSession session = login("13800000005");
                mockMvc.perform(post("/api/customer/account/terminate").session(session))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.reasons.length()").value(2));
        }

        @Test
        void 无未了结事项客户解约成功且权限关闭() throws Exception {
                MockHttpSession session = login("13800000001");
                mockMvc.perform(post("/api/customer/account/terminate").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("TERMINATED"))
                                .andExpect(jsonPath("$.permissions[?(@.type=='PROPRIETARY')].status").value("CLOSED"))
                                .andExpect(jsonPath("$.permissions[?(@.type=='CONSIGNMENT')].status").value("CLOSED"));
        }

        @Test
        void 测评过期客户返回过期标志() throws Exception {
                MockHttpSession session = login("13800000006");
                mockMvc.perform(get("/api/customer/assessment").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.riskLevel").value("C4"))
                                .andExpect(jsonPath("$.expired").value(true));
        }

        @Test
        void 有效测评客户返回等级与未过期标志() throws Exception {
                MockHttpSession session = login("13800000003");
                mockMvc.perform(get("/api/customer/assessment").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.riskLevel").value("C3"))
                                .andExpect(jsonPath("$.expired").value(false));
        }

        @Test
        void 测评答案格式不合法返回400() throws Exception {
                MockHttpSession session = login("13800000001");
                mockMvc.perform(post("/api/customer/assessment").session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"answers\":\"A,X,B\"}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("PARAM_INVALID"));
        }

        @Test
        void 签署不支持的文件类型返回400且签署幂等() throws Exception {
                MockHttpSession session = login("13800000001");
                mockMvc.perform(post("/api/customer/documents/UNKNOWN_TYPE/sign").session(session))
                                .andExpect(status().isBadRequest());

                // 重复签署 RIGHTS_NOTICE：幂等，不新增记录（种子已签 20 份）
                long before = em.createQuery(
                                "select count(d) from SignDocument d", Long.class).getSingleResult();
                mockMvc.perform(post("/api/customer/documents/RIGHTS_NOTICE/sign").session(session))
                                .andExpect(status().isOk());
                long after = em.createQuery(
                                "select count(d) from SignDocument d", Long.class).getSingleResult();
                org.assertj.core.api.Assertions.assertThat(after).isEqualTo(before);
        }
}

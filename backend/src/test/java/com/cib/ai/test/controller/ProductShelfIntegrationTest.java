package com.cib.ai.test.controller;

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
 * 产品货架域集成测试（spec 功能 7/8/9）：
 * 统一货架全量/多条件筛选、代销风险提示、详情费率与净值走势、外部来源标注、登录保护。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductShelfIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        private MockHttpSession login() throws Exception {
                MvcResult result = mockMvc.perform(post("/api/customer/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"mobile":"13800000001","password":"Passw0rd!","channelCode":"PC_WEB"}
                                                """))
                                .andExpect(status().isOk())
                                .andReturn();
                return (MockHttpSession) result.getRequest().getSession();
        }

        @Test
        void 统一货架返回全部产品且带最新净值() throws Exception {
                MockHttpSession session = login();
                mockMvc.perform(get("/api/customer/products").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(8))
                                .andExpect(jsonPath("$.products.length()").value(8))
                                // 每只产品均有发行方与类型标识 + 最新净值（2026-09-15）
                                .andExpect(jsonPath("$.products[0].issuerName").isNotEmpty())
                                .andExpect(jsonPath("$.products[0].productType").isNotEmpty())
                                .andExpect(jsonPath("$.products[0].latestNav").isNotEmpty())
                                .andExpect(jsonPath("$.products[0].latestNavDate").value("2026-09-15"));
        }

        @Test
        void 代销产品全部带风险提示_自营不带() throws Exception {
                MockHttpSession session = login();
                MvcResult result = mockMvc.perform(get("/api/customer/products")
                                .session(session).param("productType", "CONSIGNMENT"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(4))
                                .andReturn();
                org.assertj.core.api.Assertions.assertThat(
                                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                                .contains("代销产品")
                                .contains("不承担产品的投资、兑付和风险管理责任");

                mockMvc.perform(get("/api/customer/products")
                                .session(session).param("productType", "PROPRIETARY"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(4))
                                .andExpect(jsonPath("$.products[0].consignmentRiskHint").doesNotExist());
        }

        @Test
        void 风险等级与品类筛选() throws Exception {
                MockHttpSession session = login();
                mockMvc.perform(get("/api/customer/products")
                                .session(session).param("riskLevel", "R5"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(2));
                mockMvc.perform(get("/api/customer/products")
                                .session(session).param("category", "基金"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(2));
                // 组合筛选：代销 + R4（P-CS-03，双录演示产品）
                mockMvc.perform(get("/api/customer/products")
                                .session(session).param("productType", "CONSIGNMENT").param("riskLevel", "R4"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(1))
                                .andExpect(jsonPath("$.products[0].productCode").value("P-CS-03"));
        }

        @Test
        void 关键词搜索匹配产品名与发行方() throws Exception {
                MockHttpSession session = login();
                mockMvc.perform(get("/api/customer/products")
                                .session(session).param("keyword", "基金"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(2));
                mockMvc.perform(get("/api/customer/products")
                                .session(session).param("issuer", "中恒信托"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(1))
                                .andExpect(jsonPath("$.products[0].productCode").value("P-CS-03"));
        }

        @Test
        void 期限与起购金额上限筛选() throws Exception {
                MockHttpSession session = login();
                mockMvc.perform(get("/api/customer/products")
                                .session(session).param("maxTermDays", "365"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(5));
                mockMvc.perform(get("/api/customer/products")
                                .session(session).param("maxMinAmount", "10000"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(4));
        }

        @Test
        void 自营产品详情含费率走势与管理端来源() throws Exception {
                MockHttpSession session = login();
                // P-PR-02 稳盈90天：费率 0.0030/0.0020，30 日走势，INTERNAL 来源
                mockMvc.perform(get("/api/customer/products/2").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.productCode").value("P-PR-02"))
                                .andExpect(jsonPath("$.purchaseFeeRate").value(0.0030))
                                .andExpect(jsonPath("$.redemptionFeeRate").value(0.0020))
                                .andExpect(jsonPath("$.navTrend.length()").value(30))
                                .andExpect(jsonPath("$.navSource.source").value("INTERNAL"))
                                .andExpect(jsonPath("$.announcements.length()").value(3))
                                .andExpect(jsonPath("$.consignmentRiskHint").doesNotExist());
        }

        @Test
        void 代销产品详情含外部来源同步时间与风险提示() throws Exception {
                MockHttpSession session = login();
                // P-CS-01 安鑫债券基金：EXTERNAL 来源 + syncedAt 标注 + 风险提示
                mockMvc.perform(get("/api/customer/products/5").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.productCode").value("P-CS-01"))
                                .andExpect(jsonPath("$.productType").value("CONSIGNMENT"))
                                .andExpect(jsonPath("$.navTrend.length()").value(30))
                                .andExpect(jsonPath("$.navSource.source").value("EXTERNAL"))
                                .andExpect(jsonPath("$.navSource.syncedAt").isNotEmpty())
                                .andExpect(jsonPath("$.navSource.note").value(
                                                "代销产品净值以发行机构（外部 TA）为准，以下为最近同步数据"))
                                .andExpect(jsonPath("$.consignmentRiskHint").isNotEmpty());
        }

        @Test
        void 产品不存在返回400() throws Exception {
                MockHttpSession session = login();
                mockMvc.perform(get("/api/customer/products/999").session(session))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("PARAM_INVALID"));
        }

        @Test
        void 未登录访问货架返回401() throws Exception {
                mockMvc.perform(get("/api/customer/products"))
                                .andExpect(status().isUnauthorized());
        }
}

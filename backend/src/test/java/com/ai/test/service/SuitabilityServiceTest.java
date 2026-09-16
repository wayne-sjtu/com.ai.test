package com.ai.test.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.test.domain.SuitabilityResult;
import com.ai.test.repository.SuitabilityLogRepository;
import com.ai.test.repository.SuitabilityRuleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 适当性引擎集成测试（设计文档第 7 章）：
 * C×R 数据驱动映射三分支（PASS/CONFIRM/BLOCK）+ 前置校验（无测评/过期/产品停用）
 * + 每次校验留痕与 confirmed 标志。引擎经订单校验链调用，此处直连 service 验证规则矩阵。
 */
@SpringBootTest
@Transactional
class SuitabilityServiceTest {

    @Autowired
    private SuitabilityService suitabilityService;

    @Autowired
    private SuitabilityLogRepository logRepository;

    @Autowired
    private SuitabilityRuleRepository ruleRepository;

    @Autowired
    private EntityManager em;

    private long logCount(String customerNo) {
        return logRepository.findByCustomerNoOrderByCreatedAtDesc(customerNo).size();
    }

    @Test
    void 规则表种子25行覆盖全部CxR组合() {
        assertThat(ruleRepository.count()).isEqualTo(25);
    }

    @Test
    void C1买R3被拦截() {
        SuitabilityResult result = suitabilityService.check("CUST2026000001", "P-PR-02", false);
        assertThat(result.action()).isEqualTo("BLOCK");
        assertThat(result.passed()).isFalse();
        assertThat(result.message()).contains("C1").contains("R3").contains("不匹配");
    }

    @Test
    void C1买R2需二次确认_确认后放行并留痕() {
        long before = logCount("CUST2026000001");
        // 未确认：needConfirm，不放行
        SuitabilityResult unconfirmed = suitabilityService.check("CUST2026000001", "P-CS-01", false);
        assertThat(unconfirmed.action()).isEqualTo("CONFIRM");
        assertThat(unconfirmed.needConfirm()).isTrue();
        assertThat(unconfirmed.passed()).isFalse();
        // 确认后：放行
        SuitabilityResult confirmed = suitabilityService.check("CUST2026000001", "P-CS-01", true);
        assertThat(confirmed.action()).isEqualTo("CONFIRM");
        assertThat(confirmed.passed()).isTrue();
        // 两次校验各留痕一条，第二次 confirmed=true
        assertThat(logCount("CUST2026000001")).isEqualTo(before + 2);
        var logs = logRepository.findByCustomerNoOrderByCreatedAtDesc("CUST2026000001");
        assertThat(logs.get(0).getConfirmed()).isTrue();
        assertThat(logs.get(1).getConfirmed()).isFalse();
    }

    @Test
    void C3买R4二次确认分支() {
        SuitabilityResult result = suitabilityService.check("CUST2026000003", "P-PR-03", false);
        assertThat(result.action()).isEqualTo("CONFIRM");
        assertThat(result.needConfirm()).isTrue();
    }

    @Test
    void C4买R4直接通过() {
        SuitabilityResult result = suitabilityService.check("CUST2026000004", "P-PR-03", false);
        assertThat(result.action()).isEqualTo("PASS");
        assertThat(result.passed()).isTrue();
    }

    @Test
    void C5买R5高风险产品通过() {
        SuitabilityResult result = suitabilityService.check("CUST2026000005", "P-CS-04", false);
        assertThat(result.action()).isEqualTo("PASS");
        assertThat(result.passed()).isTrue();
    }

    @Test
    void 测评过期强制重测拦截() {
        SuitabilityResult result = suitabilityService.check("CUST2026000006", "P-PR-01", false);
        assertThat(result.action()).isEqualTo("BLOCK");
        assertThat(result.message()).contains("过期").contains("重新完成风险测评");
    }

    @Test
    void 未测评客户拦截并引导() {
        SuitabilityResult result = suitabilityService.check("CUST2026000007", "P-PR-01", false);
        assertThat(result.action()).isEqualTo("BLOCK");
        assertThat(result.message()).contains("未完成风险测评");
    }

    @Test
    void 产品停用状态拦截交易() {
        em.createQuery("update Product p set p.status = 'SUSPENDED' where p.productCode = 'P-PR-01'")
                .executeUpdate();
        SuitabilityResult result = suitabilityService.check("CUST2026000004", "P-PR-01", false);
        assertThat(result.action()).isEqualTo("BLOCK");
        assertThat(result.message()).contains("SUSPENDED");
    }

    @Test
    void 产品不存在抛参数异常() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> suitabilityService.check("CUST2026000004", "P-XX-99", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("产品不存在");
    }

    @Test
    void 每次校验均写留痕含等级上下文() {
        long before = logCount("CUST2026000003");
        suitabilityService.check("CUST2026000003", "P-PR-02", false);
        var latest = logRepository.findByCustomerNoOrderByCreatedAtDesc("CUST2026000003").get(0);
        assertThat(latest.getCustomerRiskLevel()).isEqualTo("C3");
        assertThat(latest.getProductRiskLevel()).isEqualTo("R3");
        assertThat(latest.getResult()).isEqualTo("PASS");
        assertThat(logCount("CUST2026000003")).isEqualTo(before + 1);
    }
}

package com.ai.test.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCrypt;

/**
 * 种子数据与实体映射验证（设计文档第 3、8 章）：
 * schema.sql / data.sql 在 H2（MySQL 方言）执行后，逐实体做 JPQL 计数，
 * 同时校验种子矩阵关键数值（演示账号密码、资金余额、额度）。
 */
@SpringBootTest
class SeedDataTest {

    @Autowired
    private EntityManager em;

    private long count(String jpql) {
        return em.createQuery(jpql, Long.class).getSingleResult();
    }

    @Test
    void 渠道与身份种子() {
        assertThat(count("select count(c) from Channel c")).isEqualTo(4);
        assertThat(count("select count(c) from Customer c")).isEqualTo(6);
        assertThat(count("select count(o) from Operator o")).isEqualTo(2);
    }

    @Test
    void 账户与准入种子() {
        assertThat(count("select count(a) from WealthAccount a")).isEqualTo(5);
        assertThat(count("select count(p) from TradingPermission p")).isEqualTo(10);
        // c3 含 1 条过期历史记录，验证测评 append-only（历史不被覆盖）
        assertThat(count("select count(r) from RiskAssessment r")).isEqualTo(6);
        assertThat(count("select count(r) from RiskAssessment r where r.customerNo = 'CUST2026000003'")).isEqualTo(2);
        assertThat(count("select count(d) from SignDocument d")).isEqualTo(20);
    }

    @Test
    void 产品与净值种子() {
        assertThat(count("select count(p) from Product p")).isEqualTo(8);
        assertThat(count("select count(p) from Product p where p.productType = 'PROPRIETARY'")).isEqualTo(4);
        assertThat(count("select count(p) from Product p where p.productType = 'CONSIGNMENT'")).isEqualTo(4);
        // 8 产品 × 30 估值日
        assertThat(count("select count(n) from ProductNav n")).isEqualTo(240);
        // 代销净值带外部同步时间
        assertThat(count("select count(n) from ProductNav n where n.source = 'EXTERNAL'")).isEqualTo(120);
    }

    @Test
    void 订单与轨迹种子() {
        assertThat(count("select count(o) from Order o")).isEqualTo(7);
        assertThat(count("select count(o) from Order o where o.status = 'SETTLED'")).isEqualTo(5);
        assertThat(count("select count(o) from Order o where o.status = 'TA_ACCEPTED'")).isEqualTo(1);
        assertThat(count("select count(o) from Order o where o.status = 'SUBMITTED'")).isEqualTo(1);
        assertThat(count("select count(e) from OrderEvent e")).isEqualTo(30);
    }

    @Test
    void 账务与持仓种子() {
        assertThat(count("select count(c) from CapitalAccount c")).isEqualTo(6);
        assertThat(count("select count(f) from CapitalFlow f")).isEqualTo(11);
        assertThat(count("select count(p) from Position p")).isEqualTo(5);
        // c4：可用 869,100 / 冻结 50,000（在途申购）
        Object[] c4 = (Object[]) em.createQuery(
                "select c.availableBalance, c.frozenBalance from CapitalAccount c where c.customerNo = 'CUST2026000004'")
                .getSingleResult();
        assertThat((BigDecimal) c4[0]).isEqualByComparingTo("869100.00");
        assertThat((BigDecimal) c4[1]).isEqualByComparingTo("50000.00");
        // P-PR-04 额度紧张（used 4,980,000 / total 5,000,000，演示额度拦截）
        Object[] quota = (Object[]) em.createQuery(
                "select p.totalQuota, p.usedQuota from Product p where p.productCode = 'P-PR-04'")
                .getSingleResult();
        assertThat((BigDecimal) quota[1]).isEqualByComparingTo("4980000.00");
        assertThat((BigDecimal) quota[0]).isEqualByComparingTo("5000000.00");
    }

    @Test
    void 服务域种子() {
        assertThat(count("select count(p) from InvestPlan p")).isEqualTo(2);
        assertThat(count("select count(d) from DividendSetting d")).isEqualTo(2);
        assertThat(count("select count(m) from Message m")).isEqualTo(8);
        assertThat(count("select count(t) from Ticket t")).isEqualTo(2);
        // 代销投诉含外部同步状态
        assertThat(count("select count(t) from Ticket t where t.externalSyncStatus is not null")).isEqualTo(1);
        assertThat(count("select count(d) from DualRecord d")).isEqualTo(0);
        assertThat(count("select count(s) from SuitabilityLog s")).isEqualTo(6);
        assertThat(count("select count(f) from Faq f")).isEqualTo(6);
    }

    @Test
    void 演示账号密码为BCrypt哈希且可校验() {
        List<String> customerHashes = em.createQuery(
                "select c.passwordHash from Customer c where c.mobile = '13800000004'", String.class)
                .getResultList();
        assertThat(customerHashes).hasSize(1);
        assertThat(BCrypt.checkpw("Passw0rd!", customerHashes.get(0))).isTrue();

        List<String> operatorHashes = em.createQuery(
                "select o.passwordHash from Operator o where o.username = 'admin_op'", String.class)
                .getResultList();
        assertThat(operatorHashes).hasSize(1);
        assertThat(BCrypt.checkpw("Admin123!", operatorHashes.get(0))).isTrue();
    }

    @Test
    void 每只产品均有最新净值可用() {
        List<Object[]> latest = em.createQuery(
                "select n.productCode, max(n.navDate) from ProductNav n group by n.productCode", Object[].class)
                .getResultList();
        assertThat(latest).hasSize(8);
        assertThat(latest).allSatisfy(row -> assertThat(row[1].toString()).isEqualTo("2026-09-15"));
    }
}

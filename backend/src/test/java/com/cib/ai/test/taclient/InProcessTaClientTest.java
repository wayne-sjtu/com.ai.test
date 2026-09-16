package com.cib.ai.test.taclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/**
 * Phase 1 / local Profile 集成测试：
 * 验证 H2（MySQL 方言）数据源与进程内双 TA Mock 的契约行为（设计文档 1.2、6.2、6.3 节）。
 */
@SpringBootTest
@Timeout(10)
class InProcessTaClientTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @Autowired
    private InProcessInternalTaClient internalTa;

    @Autowired
    private InProcessExternalTaClient externalTa;

    @BeforeEach
    void resetFaultMode() {
        externalTa.setFaultMode(FaultMode.NORMAL);
    }

    private OrderCommand purchaseCmd(String orderNo) {
        return new OrderCommand(orderNo, "P-PR-01", "cust_c4", new BigDecimal("10000"), null);
    }

    @Test
    void localProfile使用H2MySQL方言数据源() throws SQLException {
        // 注：H2 DatabaseMetaData.getURL() 返回归一化短 URL（不含属性），故从 Environment 校验配置契约
        String configuredUrl = environment.getProperty("spring.datasource.url");
        assertThat(configuredUrl).contains("jdbc:h2:mem").contains("MODE=MySQL");

        // 行为校验：MySQL 方式连接成功
        assertThat(dataSource.getConnection().getMetaData().getURL()).startsWith("jdbc:h2:mem");
    }

    @Test
    void 本行TA申购同步确认并返回流水号与份额() {
        TaResult result = internalTa.subscribe(purchaseCmd("ORD-TEST-001"));

        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.serialNo()).startsWith("INTA-");
        assertThat(result.confirmedShares()).isEqualByComparingTo("10000");
    }

    @Test
    void 本行TA赎回按份额确认() {
        OrderCommand cmd = new OrderCommand("ORD-TEST-002", "P-PR-02", "cust_c5", null, new BigDecimal("5000"));
        TaResult result = internalTa.redeem(cmd);

        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.confirmedShares()).isEqualByComparingTo("5000");
    }

    @Test
    void 外部TA正常受理后异步回调确认() throws InterruptedException {
        TaResult result = externalTa.subscribe(purchaseCmd("ORD-TEST-003"));

        assertThat(result.status()).isEqualTo("ACCEPTED");
        assertThat(result.serialNo()).startsWith("EXTA-");

        awaitCallback(1, Duration.ofSeconds(3));
        assertThat(externalTa.processedCallbackCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void 外部TA延迟确认模式回调延后到达() throws InterruptedException {
        externalTa.setFaultMode(FaultMode.DELAY_CONFIRM);
        int before = externalTa.processedCallbackCount();

        TaResult result = externalTa.subscribe(purchaseCmd("ORD-TEST-004"));
        assertThat(result.status()).isEqualTo("ACCEPTED");

        awaitCallback(before + 1, Duration.ofSeconds(5));
        assertThat(externalTa.processedCallbackCount()).isGreaterThan(before);
    }

    @Test
    void 外部TA拒绝模式返回REJECTED() {
        externalTa.setFaultMode(FaultMode.REJECT);

        TaResult result = externalTa.subscribe(purchaseCmd("ORD-TEST-005"));

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.serialNo()).startsWith("EXTA-");
    }

    @Test
    void 外部TA超时模式抛通信异常() {
        externalTa.setFaultMode(FaultMode.TIMEOUT);

        assertThatThrownBy(() -> externalTa.subscribe(purchaseCmd("ORD-TEST-006")))
                .isInstanceOf(TaCommunicationException.class)
                .hasMessageContaining("ORD-TEST-006");
    }

    @Test
    void 重复回调按流水号幂等去重() {
        int before = externalTa.processedCallbackCount();
        TaCallback cb = new TaCallback("EXTA-CALLBACK-DUP-00000001", "ORD-TEST-007", "CONFIRMED",
                new BigDecimal("10000"), "重复回调测试");

        externalTa.confirmCallback(cb);
        externalTa.confirmCallback(cb);

        assertThat(externalTa.processedCallbackCount()).isEqualTo(before + 1);
    }

    /** 轮询等待异步回调到达（无 Awaitility 依赖，简单轮询实现） */
    private void awaitCallback(int expectedCount, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (externalTa.processedCallbackCount() < expectedCount && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
    }
}

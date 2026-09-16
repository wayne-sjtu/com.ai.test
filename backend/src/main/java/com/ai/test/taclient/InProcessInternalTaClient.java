package com.ai.test.taclient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 本行 TA 进程内 Mock（Phase 1 / local Profile，设计文档 6.2 节）。
 * <p>
 * 行为：受理即同步确认，份额按 Mock 净值计算。
 * Phase 2 切换为 HttpInternalTaClient（compose Profile），契约不变。
 */
@Component
@Profile("local")
public class InProcessInternalTaClient implements TaClient {

    /** Mock 净值：Phase 1 尚无产品净值数据，固定 1.0000；Phase 2 由 HTTP Mock 读取种子净值 */
    private static final BigDecimal MOCK_NAV = new BigDecimal("1.0000");

    private final TaSerialGenerator serialGen = new TaSerialGenerator("INTA");

    @Override
    public TaResult subscribe(OrderCommand cmd) {
        return confirm(cmd);
    }

    @Override
    public TaResult redeem(OrderCommand cmd) {
        return confirm(cmd);
    }

    /** 分红方式设置：本行 TA 登记同步生效 */
    @Override
    public TaResult setDividendType(DividendCommand cmd) {
        return new TaResult(serialGen.next(), "CONFIRMED", null,
                "本行TA分红方式登记成功：" + cmd.dividendType());
    }

    private TaResult confirm(OrderCommand cmd) {
        BigDecimal shares = cmd.shares() != null
                ? cmd.shares()
                : cmd.amount().divide(MOCK_NAV, 2, RoundingMode.HALF_UP);
        return new TaResult(serialGen.next(), "CONFIRMED", shares, "本行TA同步确认");
    }
}

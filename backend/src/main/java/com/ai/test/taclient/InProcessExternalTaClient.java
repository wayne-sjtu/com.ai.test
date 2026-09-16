package com.ai.test.taclient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 外部 TA 进程内 Mock（Phase 1 / local Profile，设计文档 6.3 节）。
 * <p>
 * 行为：受理即返回 ACCEPTED，异步延迟回调确认（触发银行侧 TaCallbackHandler，
 * 等价于真实 HTTP 回调）；支持故障注入开关；回调按报文流水号幂等去重。
 * Phase 2 切换为 HttpExternalTaClient（compose Profile，真实 HTTP 边界 + 30s 超时挂起）。
 */
@Component
@Profile("local")
public class InProcessExternalTaClient implements ExternalTaClient {

    /** Mock 净值：Phase 1 尚无产品净值数据，固定 1.0000 */
    private static final BigDecimal MOCK_NAV = new BigDecimal("1.0000");

    /** 演示级回调延迟：正常 200ms；延迟确认 1s（Phase 2 HTTP Mock 为 60s，可配置） */
    private static final long NORMAL_CALLBACK_DELAY_MS = 200;
    private static final long DELAYED_CALLBACK_DELAY_MS = 1_000;

    private final TaSerialGenerator serialGen = new TaSerialGenerator("EXTA");
    /** 已处理回调的报文流水号集合：重复回调幂等去重 */
    private final Set<String> processedCallbacks = ConcurrentHashMap.newKeySet();
    /** 银行侧回调处理器（等价真实 HTTP 回调端点） */
    private final ObjectProvider<TaCallbackHandler> callbackHandler;

    private volatile FaultMode faultMode = FaultMode.NORMAL;

    public InProcessExternalTaClient(ObjectProvider<TaCallbackHandler> callbackHandler) {
        this.callbackHandler = callbackHandler;
    }

    @Override
    public TaResult subscribe(OrderCommand cmd) {
        return accept(cmd);
    }

    @Override
    public TaResult redeem(OrderCommand cmd) {
        return accept(cmd);
    }

    /** 分红方式设置：外部 TA 受理（同步发行机构登记，正常模式受理成功） */
    @Override
    public TaResult setDividendType(DividendCommand cmd) {
        if (faultMode == FaultMode.TIMEOUT) {
            throw new TaCommunicationException("外部TA通信超时（模拟）: 分红设置 " + cmd.productCode());
        }
        return new TaResult(serialGen.next(), "ACCEPTED", null,
                "外部TA已受理分红方式登记：" + cmd.dividendType());
    }

    @Override
    public void confirmCallback(TaCallback cb) {
        // 幂等：同一报文流水号只处理一次（差错矩阵"重复回调"场景）
        processedCallbacks.add(cb.serialNo());
    }

    @Override
    public void setFaultMode(FaultMode mode) {
        this.faultMode = mode;
    }

    public FaultMode getFaultMode() {
        return faultMode;
    }

    /** 已处理回调报文数（供测试与演示观察） */
    public int processedCallbackCount() {
        return processedCallbacks.size();
    }

    private TaResult accept(OrderCommand cmd) {
        switch (faultMode) {
            case TIMEOUT -> throw new TaCommunicationException("外部TA通信超时（模拟）: " + cmd.orderNo());
            case REJECT -> {
                return new TaResult(serialGen.next(), "REJECTED", null, "外部TA受理后拒绝");
            }
            default -> {
                /* NORMAL / DELAY_CONFIRM 继续受理 */ }
        }
        String serialNo = serialGen.next();
        long delay = faultMode == FaultMode.DELAY_CONFIRM ? DELAYED_CALLBACK_DELAY_MS : NORMAL_CALLBACK_DELAY_MS;
        BigDecimal shares = cmd.shares() != null
                ? cmd.shares()
                : cmd.amount().divide(MOCK_NAV, 2, RoundingMode.HALF_UP);
        // 异步模拟外部 TA 延迟确认回调：触发银行侧处理器（等价真实 HTTP 回调端点）
        CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(() -> {
            TaCallback cb = new TaCallback(serialGen.next(), cmd.orderNo(), "CONFIRMED",
                    shares, "外部TA异步确认");
            TaCallbackHandler handler = callbackHandler.getIfAvailable();
            if (handler != null) {
                handler.handle(cb);
            }
            confirmCallback(cb);
        });
        return new TaResult(serialNo, "ACCEPTED", null, "外部TA已受理，等待异步确认");
    }
}

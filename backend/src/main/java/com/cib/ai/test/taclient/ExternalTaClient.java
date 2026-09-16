package com.cib.ai.test.taclient;

/**
 * 外部 TA 扩展契约：异步确认回调 + 故障注入开关（设计文档 6.3 节）。
 */
public interface ExternalTaClient extends TaClient {

    /**
     * 确认回调入口：外部 TA 异步确认后经此送达主系统。
     * 实现必须按报文流水号幂等去重（差错矩阵"重复回调"场景）。
     */
    void confirmCallback(TaCallback cb);

    /** 故障注入开关：NORMAL / TIMEOUT / REJECT / DELAY_CONFIRM */
    void setFaultMode(FaultMode mode);

    /** 当前故障模式（健康检查组件矩阵展示） */
    FaultMode getFaultMode();
}

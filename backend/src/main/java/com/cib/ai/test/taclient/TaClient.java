package com.cib.ai.test.taclient;

/**
 * TA（登记过户机构）统一访问抽象（设计文档 6.1 节）。
 * <p>
 * Phase 1（local Profile）：进程内 Mock 实现；
 * Phase 2（compose Profile）：HTTP 实现（本行/外部 TA Mock 容器）。
 * 两套实现签名与报文结构一致，业务代码零改动。
 */
public interface TaClient {

    /** 申购指令 */
    TaResult subscribe(OrderCommand cmd);

    /** 赎回指令 */
    TaResult redeem(OrderCommand cmd);

    /** 分红方式设置指令（功能 12：现金分红/红利再投资走 TA 报文） */
    TaResult setDividendType(DividendCommand cmd);

    /** 存活探测（健康检查组件矩阵用；compose 实现为 HTTP ping + 超时） */
    default boolean ping() {
        return true;
    }
}

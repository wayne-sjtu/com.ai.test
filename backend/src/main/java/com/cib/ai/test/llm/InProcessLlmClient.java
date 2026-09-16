package com.cib.ai.test.llm;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 进程内 LLM Mock（Phase 1 local Profile）：
 * 按投教主题模板生成回答（模拟 LLM 生成），主题外问题返回 empty（模拟 LLM 拒答），
 * 支持可用性开关演示 FAQ 降级路径（spec：降级路径必须同等可演示）。
 */
@Component
@ConditionalOnProperty(name = "ai.llm.provider", havingValue = "mock", matchIfMissing = true)
public class InProcessLlmClient implements LlmClient {

    /** 主题模板：关键词 → 回答（模拟 LLM 对投教知识的理解与生成） */
    private static final List<Topic> TOPICS = List.of(
            new Topic(List.of("净值", "单位净值"),
                    "单位净值指每份理财产品的净资产价值，由管理人按估值日计算公布。"
                            + "持仓市值 = 持有份额 × 最新单位净值，净值随市场波动，历史业绩不代表未来表现。"),
            new Topic(List.of("收益", "收益率", "赚"),
                    "理财产品收益取决于产品类型与市场表现：固定收益类相对稳健，混合类波动较大。"
                            + "收益 =（赎回净值 − 申购净值）× 份额 − 相关费用，产品均不承诺保本保收益。"),
            new Topic(List.of("定投", "定期定额"),
                    "定投指按约定周期（如每月）自动申购固定金额的理财产品，可平摊申购成本、分散时点风险。"
                            + "您可在交易页创建定投计划，系统到期自动发起申购。"),
            new Topic(List.of("费率", "手续费", "费用"),
                    "理财产品费用一般包括申购费、赎回费与管理费：申购费在下单时计提，"
                            + "赎回费与持有期限相关（持有越久费率越低），管理费已反映在净值中。具体费率以产品说明书为准。"),
            new Topic(List.of("风险等级", "R1", "R2", "R3", "R4", "R5"),
                    "产品风险等级 R1（低）至 R5（高）递增，反映本金亏损可能性的大小。"
                            + "购买前需完成风险测评，且产品风险等级不能超过您的风险承受能力对应等级。"));

    /** 可用性开关：false 模拟 LLM 服务不可用（降级演示与健康检查） */
    private volatile boolean available = true;

    @Override
    public Optional<String> ask(String question) {
        if (!available || question == null) {
            return Optional.empty();
        }
        return TOPICS.stream()
                .filter(t -> t.keywords().stream().anyMatch(question::contains))
                .findFirst()
                .map(Topic::answer);
    }

    @Override
    public boolean available() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    private record Topic(List<String> keywords, String answer) {
    }
}

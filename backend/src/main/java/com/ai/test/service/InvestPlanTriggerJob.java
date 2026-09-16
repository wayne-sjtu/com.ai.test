package com.ai.test.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 投资计划到期触发调度（spec 用户故事 22，功能 12 分级）：
 * 周期扫描 ACTIVE 计划，nextTriggerDate 到期即自动发起申购（复用 purchase 校验链）。
 */
@Component
public class InvestPlanTriggerJob {

    private final InvestPlanService investPlanService;

    public InvestPlanTriggerJob(InvestPlanService investPlanService) {
        this.investPlanService = investPlanService;
    }

    @Scheduled(fixedDelay = 5000)
    public void triggerDuePlans() {
        investPlanService.triggerDuePlans();
    }
}

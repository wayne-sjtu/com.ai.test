package com.ai.test.service;

import com.ai.test.domain.SuitabilityResult;
import com.ai.test.repository.ProductRepository;
import com.ai.test.repository.RiskAssessmentRepository;
import com.ai.test.repository.SuitabilityLogRepository;
import com.ai.test.repository.SuitabilityRuleRepository;
import com.ai.test.repository.entity.Product;
import com.ai.test.repository.entity.RiskAssessment;
import com.ai.test.repository.entity.SuitabilityLog;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 适当性引擎（设计文档第 7 章，独立 service）：
 * 校验链 = 测评有效期 → 产品开放状态 → C×R 数据驱动映射（PASS/CONFIRM/BLOCK）；
 * 每次校验写 suitability_log 留痕，CONFIRM 档经客户二次确认（confirmRisk=true）后放行。
 * 交易域（申购/赎回校验链）通过本引擎完成适当性判定。
 */
@Service
public class SuitabilityService {

    private final RiskAssessmentRepository riskAssessmentRepository;
    private final ProductRepository productRepository;
    private final SuitabilityRuleRepository ruleRepository;
    private final SuitabilityLogRepository logRepository;

    public SuitabilityService(RiskAssessmentRepository riskAssessmentRepository,
                              ProductRepository productRepository,
                              SuitabilityRuleRepository ruleRepository,
                              SuitabilityLogRepository logRepository) {
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.productRepository = productRepository;
        this.ruleRepository = ruleRepository;
        this.logRepository = logRepository;
    }

    /** 适当性校验（无关联订单场景） */
    @Transactional
    public SuitabilityResult check(String customerNo, String productCode, boolean confirmRisk) {
        return check(customerNo, productCode, confirmRisk, null);
    }

    /**
     * 适当性校验：任一前置失败即终止（赛题红线）。
     * 每次调用（无论结果）均写一条留痕；CONFIRM 档 confirmRisk=true 时 confirmed 置位。
     */
    @Transactional
    public SuitabilityResult check(String customerNo, String productCode, boolean confirmRisk,
                                   String orderNo) {
        Product product = productRepository.findByProductCode(productCode)
                .orElseThrow(() -> new IllegalArgumentException("产品不存在：" + productCode));

        // 前置 1：测评存在性
        Optional<RiskAssessment> latest = riskAssessmentRepository.findLatest(customerNo);
        if (latest.isEmpty()) {
            return logAndReturn(customerNo, product, "NONE",
                    SuitabilityResult.block("未完成风险测评，请先完成风险测评"), false, orderNo);
        }
        RiskAssessment assessment = latest.get();

        // 前置 2：测评有效期（过期强制重测，设计文档 7 章）
        if (assessment.getValidTo().isBefore(LocalDateTime.now())) {
            return logAndReturn(customerNo, product, assessment.getRiskLevel(),
                    SuitabilityResult.block("风险测评已过期，请重新完成风险测评后再交易"), false, orderNo);
        }

        // 前置 3：产品开放状态
        if (!"OPEN".equals(product.getStatus())) {
            return logAndReturn(customerNo, product, assessment.getRiskLevel(),
                    SuitabilityResult.block("产品当前状态为 " + product.getStatus() + "，不可交易"), false, orderNo);
        }

        // C×R 数据驱动映射
        String action = ruleRepository
                .findByCustomerLevelAndProductLevel(assessment.getRiskLevel(), product.getRiskLevel())
                .map(rule -> rule.getAction())
                .orElse("BLOCK");
        String cLevel = assessment.getRiskLevel();
        String rLevel = product.getRiskLevel();

        return switch (action) {
            case "PASS" -> logAndReturn(customerNo, product, cLevel,
                    SuitabilityResult.pass(), false, orderNo);
            case "CONFIRM" -> confirmRisk
                    ? logAndReturn(customerNo, product, cLevel,
                            SuitabilityResult.confirmed("产品风险等级 " + rLevel + " 高于您的风险等级 " + cLevel
                                    + "，已确认风险并自主决策"), true, orderNo)
                    : logAndReturn(customerNo, product, cLevel,
                            SuitabilityResult.needConfirm("产品风险等级 " + rLevel + " 高于您的风险等级 " + cLevel
                                    + "，请确认风险后重新提交（confirmRisk=true）"), false, orderNo);
            default -> logAndReturn(customerNo, product, cLevel,
                    SuitabilityResult.block("客户风险等级 " + cLevel + " 与产品风险等级 " + rLevel
                            + " 不匹配，适当性校验不通过"), false, orderNo);
        };
    }

    private SuitabilityResult logAndReturn(String customerNo, Product product, String cLevel,
                                           SuitabilityResult result, boolean confirmed, String orderNo) {
        SuitabilityLog log = new SuitabilityLog();
        log.setCustomerNo(customerNo);
        log.setProductCode(product.getProductCode());
        log.setCustomerRiskLevel(cLevel);
        log.setProductRiskLevel(product.getRiskLevel());
        log.setResult(result.action());
        log.setConfirmed(confirmed);
        log.setOrderNo(orderNo);
        log.setCreatedAt(LocalDateTime.now());
        logRepository.save(log);
        return result;
    }
}

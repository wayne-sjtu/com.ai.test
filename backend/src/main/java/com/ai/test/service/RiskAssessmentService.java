package com.ai.test.service;

import com.ai.test.domain.RiskLevelRule;
import com.ai.test.model.AssessmentResponse;
import com.ai.test.repository.RiskAssessmentRepository;
import com.ai.test.repository.entity.RiskAssessment;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 风险测评用例（spec 功能 5）：append-only 快照表，仅插入、禁更新删除；
 * 有效期 1 年，expired 标志供交易前置校验强制重测引导。
 */
@Service
public class RiskAssessmentService {

    private final RiskAssessmentRepository riskAssessmentRepository;

    public RiskAssessmentService(RiskAssessmentRepository riskAssessmentRepository) {
        this.riskAssessmentRepository = riskAssessmentRepository;
    }

    /** 提交测评：校验答案 → 计算得分与 C 等级 → 插入快照（历史全部保留） */
    @Transactional
    public AssessmentResponse submit(String customerNo, String answers) {
        int score = RiskLevelRule.score(answers);
        LocalDateTime now = LocalDateTime.now();
        RiskAssessment assessment = new RiskAssessment();
        assessment.setCustomerNo(customerNo);
        assessment.setAnswers(answers);
        assessment.setScore(score);
        assessment.setRiskLevel(RiskLevelRule.level(score));
        assessment.setValidFrom(now);
        assessment.setValidTo(now.plusYears(1));
        assessment.setCreatedAt(now);
        riskAssessmentRepository.save(assessment);
        return AssessmentResponse.from(assessment, false);
    }

    /** 最新测评（无记录返回 empty，由 Controller 决定 404 语义） */
    public Optional<AssessmentResponse> latest(String customerNo) {
        return riskAssessmentRepository.findLatest(customerNo)
                .map(a -> AssessmentResponse.from(a, a.getValidTo().isBefore(LocalDateTime.now())));
    }
}

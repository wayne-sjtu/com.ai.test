package com.ai.test.repository;

import com.ai.test.repository.entity.RiskAssessment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 风险测评数据访问（append-only：只插入，不提供修改删除入口） */
public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {

    List<RiskAssessment> findByCustomerNoOrderByCreatedAtDesc(String customerNo);

    /** 最新一条测评（数据库层 Top 1，避免 append-only 历史全量加载） */
    Optional<RiskAssessment> findTopByCustomerNoOrderByCreatedAtDesc(String customerNo);

    default Optional<RiskAssessment> findLatest(String customerNo) {
        return findTopByCustomerNoOrderByCreatedAtDesc(customerNo);
    }
}

package com.cib.ai.test.repository;

import com.cib.ai.test.repository.entity.SuitabilityRule;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 适当性 C×R 映射规则数据访问 */
public interface SuitabilityRuleRepository extends JpaRepository<SuitabilityRule, Long> {

    Optional<SuitabilityRule> findByCustomerLevelAndProductLevel(String customerLevel, String productLevel);
}

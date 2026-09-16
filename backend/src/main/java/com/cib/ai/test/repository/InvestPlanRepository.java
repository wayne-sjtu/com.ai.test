package com.cib.ai.test.repository;

import com.cib.ai.test.repository.entity.InvestPlan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 投资计划数据访问（预约申购/定投，@Scheduled 扫描到期触发下单） */
public interface InvestPlanRepository extends JpaRepository<InvestPlan, Long> {

    List<InvestPlan> findByCustomerNoOrderByCreatedAtDesc(String customerNo);

    List<InvestPlan> findByStatus(String status);

    Optional<InvestPlan> findByPlanNo(String planNo);
}

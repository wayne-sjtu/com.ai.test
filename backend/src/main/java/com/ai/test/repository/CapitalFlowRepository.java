package com.ai.test.repository;

import com.ai.test.repository.entity.CapitalFlow;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 资金流水数据访问（幂等键 = 订单号 + 动作类型） */
public interface CapitalFlowRepository extends JpaRepository<CapitalFlow, Long> {

    Optional<CapitalFlow> findByOrderNoAndActionType(String orderNo, String actionType);

    List<CapitalFlow> findByCustomerNoOrderByCreatedAtDesc(String customerNo);
}

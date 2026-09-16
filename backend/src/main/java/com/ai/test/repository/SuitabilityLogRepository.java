package com.ai.test.repository;

import com.ai.test.repository.entity.SuitabilityLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 适当性校验留痕数据访问（每次校验一条，管理端可查） */
public interface SuitabilityLogRepository extends JpaRepository<SuitabilityLog, Long> {

    List<SuitabilityLog> findByCustomerNoOrderByCreatedAtDesc(String customerNo);

    List<SuitabilityLog> findByOrderNo(String orderNo);
}

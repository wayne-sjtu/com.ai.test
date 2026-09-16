package com.cib.ai.test.repository;

import com.cib.ai.test.repository.entity.DualRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 双录数据访问（代销 R4/R5 交易前置判定） */
public interface DualRecordRepository extends JpaRepository<DualRecord, Long> {

    Optional<DualRecord> findFirstByCustomerNoAndProductCodeAndStatusOrderByIdDesc(
            String customerNo, String productCode, String status);
}

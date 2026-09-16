package com.ai.test.repository;

import com.ai.test.repository.entity.DividendSetting;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 分红方式设置数据访问（CASH 现金分红 / REINVEST 红利再投资） */
public interface DividendSettingRepository extends JpaRepository<DividendSetting, Long> {

    List<DividendSetting> findByCustomerNo(String customerNo);

    Optional<DividendSetting> findByCustomerNoAndProductCode(String customerNo, String productCode);
}

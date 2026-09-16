package com.cib.ai.test.repository;

import com.cib.ai.test.repository.entity.WealthAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 理财账户数据访问（签约状态机：UNSIGNED → SIGNED → TERMINATED） */
public interface WealthAccountRepository extends JpaRepository<WealthAccount, Long> {

    Optional<WealthAccount> findByCustomerNo(String customerNo);
}

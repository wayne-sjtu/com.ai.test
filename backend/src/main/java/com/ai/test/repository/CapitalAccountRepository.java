package com.ai.test.repository;

import com.ai.test.repository.entity.CapitalAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 资金账户数据访问（简化账务：可用/冻结余额） */
public interface CapitalAccountRepository extends JpaRepository<CapitalAccount, Long> {

    Optional<CapitalAccount> findByCustomerNo(String customerNo);

    /** 悲观行锁读取（冻结/扣划/解冻/回款在事务内取锁，防止并发丢失更新） */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CapitalAccount c where c.customerNo = :customerNo")
    Optional<CapitalAccount> findByCustomerNoForUpdate(@Param("customerNo") String customerNo);
}

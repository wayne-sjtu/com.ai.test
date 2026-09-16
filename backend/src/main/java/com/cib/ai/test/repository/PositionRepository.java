package com.cib.ai.test.repository;

import com.cib.ai.test.repository.entity.Position;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 持仓数据访问（乐观锁 version 控制并发更新） */
public interface PositionRepository extends JpaRepository<Position, Long> {

    List<Position> findByCustomerNo(String customerNo);

    Optional<Position> findByCustomerNoAndProductCode(String customerNo, String productCode);

    /** 持仓悲观行锁读取（赎回冻结份额：可用校验与原子更新同一把锁，防并发超卖） */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Position p where p.customerNo = :customerNo and p.productCode = :productCode")
    Optional<Position> findByCustomerNoAndProductCodeForUpdate(@Param("customerNo") String customerNo,
            @Param("productCode") String productCode);

    /** 按产品查全部持仓（净值发布推送持有人，避免全表扫描） */
    List<Position> findByProductCode(String productCode);

    /** 产品总份额（巨额赎回阈值基数，全体持有人合计） */
    @Query("select coalesce(sum(p.shares), 0) from Position p where p.productCode = :productCode")
    java.math.BigDecimal sumSharesByProductCode(String productCode);
}

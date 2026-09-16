package com.ai.test.repository;

import com.ai.test.repository.entity.ProductNav;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 产品净值数据访问（自营管理端发布 / 代销外部 TA 同步） */
public interface ProductNavRepository extends JpaRepository<ProductNav, Long> {

    /** 每产品每估值日唯一（发布重复日期校验） */
    Optional<ProductNav> findByProductCodeAndNavDate(String productCode, LocalDate navDate);

    /** 净值走势（升序），估值计算与详情走势图共用 */
    List<ProductNav> findByProductCodeOrderByNavDateAsc(String productCode);

    /** 最新已确认净值（赎回回款金额 = 份额 × 最新净值） */
    ProductNav findFirstByProductCodeOrderByNavDateDesc(String productCode);

    /** 每只产品的最新一条净值（货架列表实时估值用） */
    @Query("""
            select n from ProductNav n
            where n.navDate = (select max(n2.navDate) from ProductNav n2 where n2.productCode = n.productCode)
            """)
    List<ProductNav> findLatestOfEachProduct();

    /** 批量查询指定产品的最新净值（统一持仓视图消除 N+1） */
    @Query("""
            select n from ProductNav n
            where n.productCode in :productCodes
              and n.navDate = (select max(n2.navDate) from ProductNav n2 where n2.productCode = n.productCode)
            """)
    List<ProductNav> findLatestByProductCodeIn(@Param("productCodes") Collection<String> productCodes);
}

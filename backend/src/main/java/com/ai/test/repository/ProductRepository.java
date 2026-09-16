package com.ai.test.repository;

import com.ai.test.repository.entity.Product;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 产品数据访问（统一货架单表，product_type 区分自营/代销） */
public interface ProductRepository extends JpaRepository<Product, Long> {

  Optional<Product> findByProductCode(String productCode);

  /** 批量查询（统一持仓视图消除 N+1） */
  List<Product> findByProductCodeIn(Collection<String> productCodes);

  /** 悲观行锁读取（额度占用防超卖，设计文档：SELECT ... FOR UPDATE） */
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Product p where p.productCode = :productCode")
  Optional<Product> findByProductCodeForUpdate(@Param("productCode") String productCode);

  /**
   * 统一货架多条件筛选（spec 功能 7/8/9）：
   * 类型/风险等级/品类/发行方/关键词（产品名、发行方或产品代码）/期限上限/起购上限，全部条件可空。
   */
  @Query("""
      select p from Product p
      where (:productType is null or p.productType = :productType)
        and (:riskLevel is null or p.riskLevel = :riskLevel)
        and (:category is null or p.category = :category)
        and (:issuer is null or p.issuerName like concat('%', :issuer, '%'))
        and (:keyword is null or p.productName like concat('%', :keyword, '%')
                          or p.issuerName like concat('%', :keyword, '%')
                          or p.productCode like concat('%', :keyword, '%'))
        and (:maxTermDays is null or p.termDays <= :maxTermDays)
        and (:maxMinAmount is null or p.minPurchaseAmount <= :maxMinAmount)
      order by p.id
      """)
  List<Product> search(@Param("productType") String productType,
      @Param("riskLevel") String riskLevel,
      @Param("category") String category,
      @Param("issuer") String issuer,
      @Param("keyword") String keyword,
      @Param("maxTermDays") Integer maxTermDays,
      @Param("maxMinAmount") java.math.BigDecimal maxMinAmount);
}

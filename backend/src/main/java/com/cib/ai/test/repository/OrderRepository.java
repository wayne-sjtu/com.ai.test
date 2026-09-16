package com.cib.ai.test.repository;

import com.cib.ai.test.repository.entity.Order;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 订单数据访问（单表 + product_type 区分自营/代销） */
public interface OrderRepository extends JpaRepository<Order, Long>,
                JpaSpecificationExecutor<Order> {

        List<Order> findByCustomerNo(String customerNo);

        List<Order> findByCustomerNoOrderByIdDesc(String customerNo);

        /** 幂等：clientRequestId 唯一，重复提交返回原订单 */
        Optional<Order> findByClientRequestId(String clientRequestId);

        Optional<Order> findByOrderNo(String orderNo);

        List<Order> findByStatus(String status);

        /** 当日赎回份额合计（巨额赎回判定基数，排除失败/撤销单；当前在途单已含其中） */
        @org.springframework.data.jpa.repository.Query("""
                        select coalesce(sum(o.shares), 0) from Order o
                        where o.productCode = :productCode and o.orderType = 'REDEEM'
                          and o.status <> 'FAILED' and o.status <> 'CANCELLED'
                          and o.createdAt >= :dayStart and o.createdAt < :dayEnd
                        """)
        java.math.BigDecimal sumRedeemSharesBetween(
                        @org.springframework.data.repository.query.Param("productCode") String productCode,
                        @org.springframework.data.repository.query.Param("dayStart") java.time.LocalDateTime dayStart,
                        @org.springframework.data.repository.query.Param("dayEnd") java.time.LocalDateTime dayEnd);
}

package com.ai.test.repository;

import com.ai.test.repository.entity.Ticket;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 客服工单数据访问（咨询/投诉；代销投诉同步发行机构闭环） */
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByCustomerNoOrderByCreatedAtDesc(String customerNo);

    /** 管理端组合筛选（条件数据库层过滤，避免全表加载内存过滤） */
    @Query("""
            select t from Ticket t
            where (:status is null or t.status = :status)
              and (:ticketType is null or t.ticketType = :ticketType)
            order by t.createdAt desc
            """)
    List<Ticket> search(@Param("status") String status, @Param("ticketType") String ticketType);

    Optional<Ticket> findByTicketNo(String ticketNo);
}

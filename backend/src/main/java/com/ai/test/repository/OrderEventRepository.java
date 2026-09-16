package com.ai.test.repository;

import com.ai.test.repository.entity.OrderEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 订单事件轨迹数据访问（append-only） */
public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {

    List<OrderEvent> findByOrderNoOrderByCreatedAtAsc(String orderNo);
}

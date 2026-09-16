package com.ai.test.service;

import com.ai.test.repository.OrderRepository;
import com.ai.test.repository.entity.Order;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 巨额赎回延期确认调度（设计文档 5.2：PENDING_QUEUE → T+1 触发 → CONFIRMED）。
 * 演示环境以可配置秒数模拟 T+1 顺延（默认 10s），生产语义为次日调度。
 */
@Component
public class PendingQueueConfirmJob {

    private static final Logger log = LoggerFactory.getLogger(PendingQueueConfirmJob.class);

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    /** 模拟 T+1 的延期秒数（演示可调） */
    private final long delaySeconds;

    public PendingQueueConfirmJob(OrderRepository orderRepository,
            OrderService orderService,
            @Value("${trade.pending-queue-delay-seconds:10}") long delaySeconds) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.delaySeconds = delaySeconds;
    }

    @Scheduled(fixedDelay = 5000)
    public void confirmDuePendingOrders() {
        LocalDateTime due = LocalDateTime.now().minusSeconds(delaySeconds);
        for (Order order : orderRepository.findByStatus("PENDING_QUEUE")) {
            if (order.getUpdatedAt().isBefore(due)) {
                log.info("巨额赎回延期确认触发：{}", order.getOrderNo());
                // 传订单号：service 在事务内重载受管实体（脱管实体修改不落库）
                orderService.confirmPendingQueueOrder(order.getOrderNo());
            }
        }
    }
}

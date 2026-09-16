package com.ai.test.service;

import com.ai.test.repository.OrderRepository;
import com.ai.test.taclient.TaCallback;
import com.ai.test.taclient.TaCallbackHandler;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 外部 TA 异步确认回调处理（差错矩阵"重复回调"场景）：
 * 幂等两层保障——报文流水号去重 + 订单状态机守卫（非 TA_ACCEPTED 跳过）。
 */
@Service
public class ExternalTaConfirmService implements TaCallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(ExternalTaConfirmService.class);

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    /** 已处理回调流水号（进程内去重，Phase 1 演示足够） */
    private final Set<String> processedSerialNos = ConcurrentHashMap.newKeySet();

    public ExternalTaConfirmService(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    @Override
    @Transactional
    public void handle(TaCallback callback) {
        if (!processedSerialNos.add(callback.serialNo())) {
            log.info("重复回调已去重：{}", callback.serialNo());
            return;
        }
        var orderOpt = orderRepository.findByOrderNo(callback.orderNo());
        if (orderOpt.isEmpty()) {
            log.warn("回调订单不存在，跳过：{}", callback.orderNo());
            return;
        }
        var order = orderOpt.get();
        if (!"TA_ACCEPTED".equals(order.getStatus())) {
            log.info("订单状态 {} 非 TA_ACCEPTED，跳过回调：{}", order.getStatus(), order.getOrderNo());
            return;
        }
        if (!"CONFIRMED".equals(callback.status())) {
            log.warn("回调状态非 CONFIRMED（{}），暂不处理：{}", callback.status(), order.getOrderNo());
            return;
        }
        // 按订单类型分派结算路径（PENDING_QUEUE 单由调度器持有，此处跳过）
        if ("PURCHASE".equals(order.getOrderType())) {
            orderService.confirmAndSettle(order, callback.serialNo(), callback.confirmedShares());
        } else {
            orderService.confirmRedeemAndSettle(order, callback.serialNo());
        }
        log.info("外部 TA 回调确认完成：订单 {}", order.getOrderNo());
    }
}

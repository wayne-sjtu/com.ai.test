package com.cib.ai.test.service;

import com.cib.ai.test.domain.OrderStateMachine;
import com.cib.ai.test.domain.SuitabilityResult;
import com.cib.ai.test.model.OrderResponse;
import com.cib.ai.test.model.PurchaseRequest;
import com.cib.ai.test.model.RedeemRequest;
import com.cib.ai.test.repository.CapitalAccountRepository;
import com.cib.ai.test.repository.DualRecordRepository;
import com.cib.ai.test.repository.OrderEventRepository;
import com.cib.ai.test.repository.OrderRepository;
import com.cib.ai.test.repository.PositionRepository;
import com.cib.ai.test.repository.ProductNavRepository;
import com.cib.ai.test.repository.ProductRepository;
import com.cib.ai.test.repository.TradingPermissionRepository;
import com.cib.ai.test.repository.WealthAccountRepository;
import com.cib.ai.test.repository.entity.Order;
import com.cib.ai.test.repository.entity.OrderEvent;
import com.cib.ai.test.repository.entity.Position;
import com.cib.ai.test.repository.entity.Product;
import com.cib.ai.test.repository.entity.ProductNav;
import com.cib.ai.test.repository.entity.TradingPermission;
import com.cib.ai.test.repository.entity.WealthAccount;
import com.cib.ai.test.taclient.OrderCommand;
import com.cib.ai.test.taclient.TaClient;
import com.cib.ai.test.taclient.TaResult;
import com.cib.ai.test.taclient.TaCommunicationException;
import com.cib.ai.test.taclient.ExternalTaClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 交易域订单用例（spec 功能 10/11，端到端主链路核心）。
 * 申购校验链：账户签约 → 交易权限 → 产品开放 → 交易时段 → 起购金额 →
 * 适当性（引擎）→ 双录判定（代销 R4/R5）→ 额度占用（行锁）→ 资金冻结，
 * 任一失败订单转 FAILED 并终止（赛题红线）。
 * TA 路由：自营 → 本行 TA 同步确认；代销 → 外部 TA 受理 + 异步回调确认。
 */
@Service
public class OrderService {

        private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyMMddHHmmss");

        private final OrderRepository orderRepository;
        private final OrderEventRepository orderEventRepository;
        private final ProductRepository productRepository;
        private final ProductNavRepository productNavRepository;
        private final WealthAccountRepository wealthAccountRepository;
        private final TradingPermissionRepository tradingPermissionRepository;
        private final CapitalService capitalService;
        private final CapitalAccountRepository capitalAccountRepository;
        private final DualRecordRepository dualRecordRepository;
        private final PositionRepository positionRepository;
        private final SuitabilityService suitabilityService;
        private final TaClient internalTaClient;
        private final ExternalTaClient externalTaClient;

        public OrderService(OrderRepository orderRepository,
                        OrderEventRepository orderEventRepository,
                        ProductRepository productRepository,
                        ProductNavRepository productNavRepository,
                        WealthAccountRepository wealthAccountRepository,
                        TradingPermissionRepository tradingPermissionRepository,
                        CapitalService capitalService,
                        CapitalAccountRepository capitalAccountRepository,
                        DualRecordRepository dualRecordRepository,
                        PositionRepository positionRepository,
                        SuitabilityService suitabilityService,
                        @org.springframework.beans.factory.annotation.Qualifier("inProcessInternalTaClient") TaClient internalTaClient,
                        @org.springframework.beans.factory.annotation.Qualifier("inProcessExternalTaClient") ExternalTaClient externalTaClient) {
                this.orderRepository = orderRepository;
                this.orderEventRepository = orderEventRepository;
                this.productRepository = productRepository;
                this.productNavRepository = productNavRepository;
                this.wealthAccountRepository = wealthAccountRepository;
                this.tradingPermissionRepository = tradingPermissionRepository;
                this.capitalService = capitalService;
                this.capitalAccountRepository = capitalAccountRepository;
                this.dualRecordRepository = dualRecordRepository;
                this.positionRepository = positionRepository;
                this.suitabilityService = suitabilityService;
                this.internalTaClient = internalTaClient;
                this.externalTaClient = externalTaClient;
        }

        /**
         * 申购下单（校验链 + 资金冻结 + TA 路由）。
         * 失败不抛异常回滚：订单转 FAILED 留痕、额度/资金回补入库，
         * 返回 PurchaseOutcome 由 Controller 映射 400。
         */
        @Transactional
        public PurchaseOutcome purchase(String customerNo, PurchaseRequest request) {
                // 幂等：同一 clientRequestId 返回原订单；历史失败单保持失败语义（须换键重试）
                var existing = orderRepository.findByClientRequestId(request.clientRequestId());
                if (existing.isPresent()) {
                        return idempotentOutcome(existing.get());
                }

                Product product = productRepository.findByProductCode(request.productCode())
                                .orElseThrow(() -> new TradeException("PRODUCT_NOT_FOUND",
                                                "产品不存在：" + request.productCode()));

                // 建单 CREATED（后续任一校验失败转 FAILED 留痕）
                Order order = createOrder(customerNo, product, request);
                try {
                        validateChain(order, customerNo, product, request);
                } catch (TradeException e) {
                        failOrder(order, null, null, "校验失败：" + e.getMessage());
                        return PurchaseOutcome.failure(order, e.getCode(), e.getMessage());
                }

                // 资金冻结（余额不足同样转 FAILED 留痕）
                try {
                        capitalService.freeze(customerNo, order.getOrderNo(), request.amount());
                } catch (TradeException e) {
                        releaseQuota(product, request.amount());
                        failOrder(order, null, null, "校验失败：" + e.getMessage());
                        return PurchaseOutcome.failure(order, e.getCode(), e.getMessage());
                }

                // SUBMITTED → TA 路由
                transition(order, "SUBMITTED", null, null, "路由至"
                                + ("PROPRIETARY".equals(product.getProductType()) ? "本行 TA" : "外部 TA"));

                TaResult result;
                try {
                        OrderCommand cmd = new OrderCommand(order.getOrderNo(), product.getProductCode(),
                                        customerNo, request.amount(), null);
                        result = "PROPRIETARY".equals(product.getProductType())
                                        ? internalTaClient.subscribe(cmd)
                                        : externalTaClient.subscribe(cmd);
                } catch (TaCommunicationException e) {
                        // 通信超时：Phase 1 简化为失败 + 解冻 + 额度释放（差错矩阵一致性由回调幂等兜底）
                        releaseQuota(product, request.amount());
                        capitalService.unfreeze(customerNo, order.getOrderNo(), request.amount());
                        failOrder(order, "EXTA", null, "TA 通信异常：" + e.getMessage());
                        return PurchaseOutcome.failure(order, "TA_COMMUNICATION_ERROR",
                                        "TA 通信异常，交易已终止并解冻资金");
                }

                String taTag = "PROPRIETARY".equals(product.getProductType()) ? "INTA" : "EXTA";
                if ("REJECTED".equals(result.status())) {
                        releaseQuota(product, request.amount());
                        capitalService.unfreeze(customerNo, order.getOrderNo(), request.amount());
                        failOrder(order, taTag, result.serialNo(), "TA 受理拒绝：" + result.message());
                        return PurchaseOutcome.failure(order, "TA_REJECTED",
                                        "TA 受理拒绝：" + result.message());
                }

                // 受理成功：TA_ACCEPTED
                order.setTaSerialNo(result.serialNo());
                transition(order, "TA_ACCEPTED", taTag, result.serialNo(),
                                "PROPRIETARY".equals(product.getProductType())
                                                ? "本行 TA 受理并同步确认"
                                                : "外部 TA 受理，等待异步确认回调");

                if ("CONFIRMED".equals(result.status())) {
                        // 本行 TA 同步确认 → 立即结算
                        confirmAndSettle(order, result.serialNo(), result.confirmedShares());
                }
                return PurchaseOutcome.success(order);
        }

        /**
         * 赎回下单（spec 功能 10/11）：校验链 → 冻结份额 → TA 路由 → 巨额赎回判定。
         * 非巨额：本行 TA 同步 REDEEMED；代销外部 TA 异步回调结算。
         * 巨额（单日赎回份额 > 产品总份额 × 阈值）：整单 PENDING_QUEUE 延期确认，由调度器 T+1 触发。
         */
        @Transactional
        public PurchaseOutcome redeem(String customerNo, RedeemRequest request) {
                var existing = orderRepository.findByClientRequestId(request.clientRequestId());
                if (existing.isPresent()) {
                        return idempotentOutcome(existing.get());
                }

                Product product = productRepository.findByProductCode(request.productCode())
                                .orElseThrow(() -> new TradeException("PRODUCT_NOT_FOUND",
                                                "产品不存在：" + request.productCode()));

                Order order = createRedeemOrder(customerNo, product, request);
                try {
                        validateRedeemChain(customerNo, product, request.shares());
                        // 冻结份额（在途赎回占用；悲观锁内复核可用份额，防并发超卖）
                        freezeShares(customerNo, product.getProductCode(), request.shares());
                } catch (TradeException e) {
                        failOrder(order, null, null, "校验失败：" + e.getMessage());
                        return PurchaseOutcome.failure(order, e.getCode(), e.getMessage());
                }

                transition(order, "SUBMITTED", null, null, "路由至"
                                + ("PROPRIETARY".equals(product.getProductType()) ? "本行 TA" : "外部 TA"));

                TaResult result;
                try {
                        OrderCommand cmd = new OrderCommand(order.getOrderNo(), product.getProductCode(),
                                        customerNo, null, request.shares());
                        result = "PROPRIETARY".equals(product.getProductType())
                                        ? internalTaClient.redeem(cmd)
                                        : externalTaClient.redeem(cmd);
                } catch (TaCommunicationException e) {
                        unfreezeShares(customerNo, product.getProductCode(), request.shares());
                        failOrder(order, "EXTA", null, "TA 通信异常：" + e.getMessage());
                        return PurchaseOutcome.failure(order, "TA_COMMUNICATION_ERROR",
                                        "TA 通信异常，交易已终止并解冻份额");
                }

                String taTag = "PROPRIETARY".equals(product.getProductType()) ? "INTA" : "EXTA";
                if ("REJECTED".equals(result.status())) {
                        unfreezeShares(customerNo, product.getProductCode(), request.shares());
                        failOrder(order, taTag, result.serialNo(), "TA 受理拒绝：" + result.message());
                        return PurchaseOutcome.failure(order, "TA_REJECTED", "TA 受理拒绝：" + result.message());
                }

                order.setTaSerialNo(result.serialNo());
                transition(order, "TA_ACCEPTED", taTag, result.serialNo(),
                                "PROPRIETARY".equals(product.getProductType())
                                                ? "本行 TA 受理"
                                                : "外部 TA 受理，等待异步确认回调");

                // 巨额赎回判定（TA 受理后、确认前）：单日累计赎回份额超产品阈值 → 延期确认
                if (isLargeRedeem(product)) {
                        transition(order, "PENDING_QUEUE", taTag, result.serialNo(),
                                        "巨额赎回：单日赎回份额超过产品总份额的 "
                                                        + product.getRedeemThresholdRate()
                                                                        .multiply(BigDecimal.valueOf(100))
                                                                        .stripTrailingZeros().toPlainString()
                                                        + "%，延期确认（T+1 顺延）");
                        return PurchaseOutcome.success(order);
                }

                if ("CONFIRMED".equals(result.status())) {
                        confirmRedeemAndSettle(order, result.serialNo());
                }
                return PurchaseOutcome.success(order);
        }

        /**
         * 赎回确认结算（本行同步路径 / 外部回调路径 / PENDING_QUEUE 调度路径共用）：
         * TA_ACCEPTED|PENDING_QUEUE → CONFIRMED → REDEEMED；回款 = 份额 × 最新净值 − 赎回费。
         * 幂等：状态守卫，非在途确认态直接跳过。
         */
        @Transactional
        public void confirmRedeemAndSettle(Order order, String taSerialNo) {
                String status = order.getStatus();
                if (!"TA_ACCEPTED".equals(status) && !"PENDING_QUEUE".equals(status)) {
                        return; // 幂等：已确认/撤销/失败的订单不重复处理
                }
                Product product = productRepository.findByProductCode(order.getProductCode())
                                .orElseThrow(() -> new TradeException("PRODUCT_NOT_FOUND", "产品不存在"));
                ProductNav latestNav = productNavRepository
                                .findFirstByProductCodeOrderByNavDateDesc(order.getProductCode());
                if (latestNav == null) {
                        // 净值缺失不可回退默认值（错误估值 = 资金差错），硬失败由回调重试/人工介入
                        throw new TradeException("NAV_NOT_FOUND",
                                        "产品 " + order.getProductCode() + " 无可用净值，暂无法确认结算");
                }
                BigDecimal nav = latestNav.getNav();

                BigDecimal shares = order.getShares();
                BigDecimal gross = shares.multiply(nav).setScale(2, RoundingMode.HALF_UP);
                BigDecimal fee = gross.multiply(product.getRedemptionFeeRate()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal net = gross.subtract(fee);
                order.setAmount(gross);
                order.setFee(fee);
                String taTag = "PROPRIETARY".equals(order.getProductType()) ? "INTA" : "EXTA";
                transition(order, "CONFIRMED", taTag, taSerialNo,
                                "TA 确认赎回 " + shares.toPlainString() + " 份，净值 " + nav.toPlainString()
                                                + "，费用 " + fee.toPlainString() + "，回款净额 " + net.toPlainString());

                // 份额最终扣减 + 解冻 + 成本按比例结转
                Position position = positionRepository
                                .findByCustomerNoAndProductCode(order.getCustomerNo(), order.getProductCode())
                                .orElseThrow(() -> new TradeException("POSITION_NOT_FOUND", "持仓不存在"));
                BigDecimal sharesBefore = position.getShares();
                BigDecimal costReduce = sharesBefore.compareTo(BigDecimal.ZERO) > 0
                                ? position.getCostAmount().multiply(shares)
                                                .divide(sharesBefore, 2, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;
                position.setShares(sharesBefore.subtract(shares));
                position.setFrozenShares(position.getFrozenShares().subtract(shares));
                position.setCostAmount(position.getCostAmount().subtract(costReduce));
                position.setUpdatedAt(LocalDateTime.now());
                positionRepository.save(position);

                // 资金回款 + 赎回确认释放销售额度
                capitalService.returnCash(order.getCustomerNo(), order.getOrderNo(), net);
                releaseQuota(product, gross);

                transition(order, "REDEEMED", null, null,
                                "份额扣减完成，资金回款 " + net.toPlainString() + " 元");
        }

        /**
         * PENDING_QUEUE 延期确认入口（调度器专用）：按订单号在事务内重新加载受管实体，
         * 避免脱管实体修改不落库。
         */
        @Transactional
        public void confirmPendingQueueOrder(String orderNo) {
                orderRepository.findByOrderNo(orderNo)
                                .filter(o -> "PENDING_QUEUE".equals(o.getStatus()))
                                .ifPresent(o -> confirmRedeemAndSettle(o, o.getTaSerialNo()));
        }

        /**
         * 撤单（spec 功能 12）：可撤时段 = 产品交易时段内、TA 确认前的在途订单。
         * 申购单解冻资金 + 释放额度；赎回单解冻份额。与 TA 回调以状态机守卫互斥。
         */
        @Transactional
        public OrderResponse cancel(String customerNo, String orderNo) {
                Order order = orderRepository.findByOrderNo(orderNo)
                                .filter(o -> customerNo.equals(o.getCustomerNo()))
                                .orElseThrow(() -> new TradeException("ORDER_NOT_FOUND", "订单不存在"));
                if (!OrderStateMachine.cancellable(order.getStatus())) {
                        throw new TradeException("ORDER_NOT_CANCELLABLE",
                                        "订单当前状态 " + order.getStatus() + " 不可撤销（仅 TA 确认前的在途订单可撤）");
                }
                Product product = productRepository.findByProductCode(order.getProductCode())
                                .orElseThrow(() -> new TradeException("PRODUCT_NOT_FOUND", "产品不存在"));
                LocalTime now = LocalTime.now();
                if (now.isBefore(product.getTradeStartTime()) || now.isAfter(product.getTradeEndTime())) {
                        throw new TradeException("TRADE_WINDOW_CLOSED",
                                        "不在可撤时段内（产品交易时段 " + product.getTradeStartTime() + " - "
                                                        + product.getTradeEndTime() + "）");
                }
                if ("PURCHASE".equals(order.getOrderType())) {
                        if (order.getAmount() != null && !"CREATED".equals(order.getStatus())) {
                                capitalService.unfreeze(customerNo, orderNo, order.getAmount());
                                releaseQuota(product, order.getAmount());
                        }
                } else if (order.getShares() != null && !"CREATED".equals(order.getStatus())) {
                        unfreezeShares(customerNo, product.getProductCode(), order.getShares());
                }
                transition(order, "CANCELLED", null, null, "客户撤单，资金/份额解冻，额度释放");
                return OrderResponse.from(order);
        }

        /**
         * 确认并结算（本行 TA 同步路径与外部 TA 回调路径共用）：
         * TA_ACCEPTED → CONFIRMED（份额入账：扣划 + 持仓）→ SETTLED。
         * 幂等：非 TA_ACCEPTED 状态直接跳过（重复回调场景）。
         */
        @Transactional
        public void confirmAndSettle(Order order, String taSerialNo, BigDecimal confirmedShares) {
                if (!"TA_ACCEPTED".equals(order.getStatus())) {
                        return; // 幂等：已确认/已失败的订单不重复处理
                }
                BigDecimal amount = order.getAmount();
                BigDecimal fee = amount.multiply(purchaseFeeRate(order.getProductCode()))
                                .setScale(2, RoundingMode.HALF_UP);
                order.setFee(fee);
                order.setShares(confirmedShares);
                String taTag = "PROPRIETARY".equals(order.getProductType()) ? "INTA" : "EXTA";
                transition(order, "CONFIRMED", taTag, taSerialNo,
                                "TA 确认份额 " + confirmedShares.toPlainString() + "，费用 " + fee.toPlainString());

                // 资金扣划（冻结 → 付出）+ 持仓入账
                capitalService.deduct(order.getCustomerNo(), order.getOrderNo(), amount);
                addPosition(order.getCustomerNo(), order.getProductCode(), confirmedShares, amount);

                transition(order, "SETTLED", null, null, "资金结清，持仓更新完成");
        }

        /** 客户订单列表（最新在前） */
        public List<OrderResponse> listByCustomer(String customerNo) {
                return orderRepository.findByCustomerNoOrderByIdDesc(customerNo).stream()
                                .map(OrderResponse::from)
                                .toList();
        }

        // ---------- 内部方法 ----------

        /** 幂等命中：失败单返回失败（客户端须更换幂等键重试），其余返回原订单 */
        private PurchaseOutcome idempotentOutcome(Order existing) {
                if ("FAILED".equals(existing.getStatus())) {
                        return PurchaseOutcome.failure(existing, "ORDER_FAILED",
                                        "该幂等请求对应的历史订单已失败，请更换 clientRequestId 后重新提交");
                }
                return PurchaseOutcome.success(existing);
        }

        private void validateChain(Order order, String customerNo, Product product,
                        PurchaseRequest request) {
                // 1. 账户签约状态
                WealthAccount account = wealthAccountRepository.findByCustomerNo(customerNo)
                                .orElseThrow(() -> new TradeException("ACCOUNT_NOT_SIGNED",
                                                "未开立理财账户，请先完成签约"));
                if (!"SIGNED".equals(account.getStatus())) {
                        throw new TradeException("ACCOUNT_NOT_SIGNED",
                                        "理财账户状态为 " + account.getStatus() + "，不可交易");
                }
                // 2. 交易权限（自营/代销独立校验）
                String requiredPermission = "PROPRIETARY".equals(product.getProductType())
                                ? "PROPRIETARY"
                                : "CONSIGNMENT";
                boolean permitted = tradingPermissionRepository.findByAccountId(account.getId()).stream()
                                .anyMatch(p -> requiredPermission.equals(p.getPermissionType())
                                                && "OPEN".equals(p.getStatus()));
                if (!permitted) {
                        throw new TradeException("PERMISSION_DENIED",
                                        "未开通" + ("PROPRIETARY".equals(requiredPermission) ? "自营" : "代销")
                                                        + "交易权限");
                }
                // 3. 产品开放状态
                if (!"OPEN".equals(product.getStatus())) {
                        throw new TradeException("PRODUCT_NOT_OPEN",
                                        "产品当前状态为 " + product.getStatus() + "，不可交易");
                }
                // 4. 交易时段
                LocalTime now = LocalTime.now();
                if (now.isBefore(product.getTradeStartTime()) || now.isAfter(product.getTradeEndTime())) {
                        throw new TradeException("TRADE_WINDOW_CLOSED",
                                        "不在交易时段内（" + product.getTradeStartTime() + " - "
                                                        + product.getTradeEndTime() + "）");
                }
                // 5. 起购金额
                if (request.amount().compareTo(product.getMinPurchaseAmount()) < 0) {
                        throw new TradeException("MIN_AMOUNT",
                                        "低于起购金额 " + product.getMinPurchaseAmount().toPlainString() + " 元");
                }
                // 6. 适当性（独立引擎，留痕关联订单号）
                SuitabilityResult suitability = suitabilityService.check(
                                customerNo, product.getProductCode(), request.confirmRisk(), order.getOrderNo());
                if (!suitability.passed()) {
                        throw new TradeException(
                                        suitability.needConfirm() ? "SUITABILITY_CONFIRM_REQUIRED"
                                                        : "SUITABILITY_BLOCKED",
                                        suitability.message());
                }
                // 7. 双录判定：代销 R4/R5 必须先完成双录（Mock 标志文件）
                boolean highRisk = "R4".equals(product.getRiskLevel()) || "R5".equals(product.getRiskLevel());
                if ("CONSIGNMENT".equals(product.getProductType()) && highRisk) {
                        boolean dualRecorded = dualRecordRepository
                                        .findFirstByCustomerNoAndProductCodeAndStatusOrderByIdDesc(
                                                        customerNo, product.getProductCode(), "COMPLETED")
                                        .isPresent();
                        if (!dualRecorded) {
                                throw new TradeException("DUAL_RECORD_REQUIRED",
                                                "代销高风险产品（" + product.getRiskLevel() + "）须先完成双录后方可交易");
                        }
                }
                // 8. 资金账户存在性（纯查询，先于额度占用，避免占用后失败泄漏额度）
                capitalAccountRepository.findByCustomerNo(customerNo)
                                .orElseThrow(() -> new TradeException("CAPITAL_ACCOUNT_NOT_FOUND", "资金账户不存在"));
                // 9. 额度占用（行锁防超卖，在途订单占额度）
                Product locked = productRepository.findByProductCodeForUpdate(product.getProductCode())
                                .orElseThrow(() -> new TradeException("PRODUCT_NOT_FOUND", "产品不存在"));
                BigDecimal remaining = locked.getTotalQuota().subtract(locked.getUsedQuota());
                if (remaining.compareTo(request.amount()) < 0) {
                        throw new TradeException("QUOTA_EXCEEDED",
                                        "产品剩余额度不足：剩余 " + remaining.toPlainString() + " 元");
                }
                locked.setUsedQuota(locked.getUsedQuota().add(request.amount()));
        }

        private Order createOrder(String customerNo, Product product, PurchaseRequest request) {
                LocalDateTime now = LocalDateTime.now();
                Order order = new Order();
                order.setOrderNo("ORD" + now.format(ORDER_NO_FORMATTER)
                                + ThreadLocalRandom.current().nextInt(100, 999));
                order.setClientRequestId(request.clientRequestId());
                order.setCustomerNo(customerNo);
                order.setProductCode(product.getProductCode());
                order.setProductType(product.getProductType());
                order.setOrderType("PURCHASE");
                order.setAmount(request.amount());
                order.setFee(BigDecimal.ZERO);
                order.setStatus("CREATED");
                order.setConfirmRisk(request.confirmRisk());
                order.setCreatedAt(now);
                order.setUpdatedAt(now);
                order = orderRepository.save(order);
                appendEvent(order, "CREATE", null, "CREATED", null, null,
                                "下单受理，金额 " + request.amount().toPlainString() + " 元");
                return order;
        }

        private Order createRedeemOrder(String customerNo, Product product, RedeemRequest request) {
                LocalDateTime now = LocalDateTime.now();
                Order order = new Order();
                order.setOrderNo("ORD" + now.format(ORDER_NO_FORMATTER)
                                + ThreadLocalRandom.current().nextInt(100, 999));
                order.setClientRequestId(request.clientRequestId());
                order.setCustomerNo(customerNo);
                order.setProductCode(product.getProductCode());
                order.setProductType(product.getProductType());
                order.setOrderType("REDEEM");
                order.setShares(request.shares());
                order.setFee(BigDecimal.ZERO);
                order.setStatus("CREATED");
                order.setConfirmRisk(false);
                order.setCreatedAt(now);
                order.setUpdatedAt(now);
                order = orderRepository.save(order);
                appendEvent(order, "CREATE", null, "CREATED", null, null,
                                "赎回受理，份额 " + request.shares().toPlainString() + " 份");
                return order;
        }

        /** 赎回校验链：签约 → 权限 → 产品状态 → 交易时段 → 持仓与可用份额 */
        private void validateRedeemChain(String customerNo, Product product, BigDecimal shares) {
                WealthAccount account = wealthAccountRepository.findByCustomerNo(customerNo)
                                .orElseThrow(() -> new TradeException("ACCOUNT_NOT_SIGNED",
                                                "未开立理财账户，请先完成签约"));
                if (!"SIGNED".equals(account.getStatus())) {
                        throw new TradeException("ACCOUNT_NOT_SIGNED",
                                        "理财账户状态为 " + account.getStatus() + "，不可交易");
                }
                String requiredPermission = "PROPRIETARY".equals(product.getProductType())
                                ? "PROPRIETARY"
                                : "CONSIGNMENT";
                boolean permitted = tradingPermissionRepository.findByAccountId(account.getId()).stream()
                                .anyMatch(p -> requiredPermission.equals(p.getPermissionType())
                                                && "OPEN".equals(p.getStatus()));
                if (!permitted) {
                        throw new TradeException("PERMISSION_DENIED",
                                        "未开通" + ("PROPRIETARY".equals(requiredPermission) ? "自营" : "代销")
                                                        + "交易权限");
                }
                if (!"OPEN".equals(product.getStatus())) {
                        throw new TradeException("PRODUCT_NOT_OPEN",
                                        "产品当前状态为 " + product.getStatus() + "，不可交易");
                }
                LocalTime now = LocalTime.now();
                if (now.isBefore(product.getTradeStartTime()) || now.isAfter(product.getTradeEndTime())) {
                        throw new TradeException("TRADE_WINDOW_CLOSED",
                                        "不在交易时段内（" + product.getTradeStartTime() + " - "
                                                        + product.getTradeEndTime() + "）");
                }
                Position position = positionRepository
                                .findByCustomerNoAndProductCode(customerNo, product.getProductCode())
                                .orElseThrow(() -> new TradeException("POSITION_NOT_FOUND",
                                                "无该产品持仓，不可赎回"));
                BigDecimal available = position.getShares().subtract(position.getFrozenShares());
                if (available.compareTo(shares) < 0) {
                        throw new TradeException("INSUFFICIENT_SHARES",
                                        "可用份额不足：可用 " + available.toPlainString() + " 份（总 "
                                                        + position.getShares().toPlainString() + " 份，冻结 "
                                                        + position.getFrozenShares().toPlainString() + " 份）");
                }
        }

        /** 巨额赎回判定：当日累计赎回份额（含在途） > 产品总份额 × 阈值比例 */
        private boolean isLargeRedeem(Product product) {
                BigDecimal outstanding = positionRepository.sumSharesByProductCode(product.getProductCode());
                BigDecimal threshold = outstanding.multiply(product.getRedeemThresholdRate());
                LocalDate today = LocalDate.now();
                BigDecimal todayRedeem = orderRepository.sumRedeemSharesBetween(
                                product.getProductCode(), today.atStartOfDay(), today.plusDays(1).atStartOfDay());
                return todayRedeem.compareTo(threshold) > 0;
        }

        /** 冻结份额（悲观行锁：可用校验与更新同一把锁内完成，防并发超卖 TOCTOU） */
        private void freezeShares(String customerNo, String productCode, BigDecimal shares) {
                Position position = positionRepository
                                .findByCustomerNoAndProductCodeForUpdate(customerNo, productCode)
                                .orElseThrow(() -> new TradeException("POSITION_NOT_FOUND", "持仓不存在"));
                BigDecimal available = position.getShares().subtract(position.getFrozenShares());
                if (available.compareTo(shares) < 0) {
                        throw new TradeException("INSUFFICIENT_SHARES",
                                        "可用份额不足：可用 " + available.toPlainString() + " 份");
                }
                position.setFrozenShares(position.getFrozenShares().add(shares));
                position.setUpdatedAt(LocalDateTime.now());
                positionRepository.save(position);
        }

        private void unfreezeShares(String customerNo, String productCode, BigDecimal shares) {
                positionRepository.findByCustomerNoAndProductCode(customerNo, productCode)
                                .ifPresent(p -> {
                                        p.setFrozenShares(p.getFrozenShares().subtract(shares));
                                        p.setUpdatedAt(LocalDateTime.now());
                                });
        }

        /** 释放额度（校验失败/TA 拒绝时回滚占用） */
        private void releaseQuota(Product product, BigDecimal amount) {
                productRepository.findByProductCodeForUpdate(product.getProductCode())
                                .ifPresent(p -> p.setUsedQuota(p.getUsedQuota().subtract(amount)));
        }

        private void addPosition(String customerNo, String productCode,
                        BigDecimal shares, BigDecimal costAmount) {
                Position position = positionRepository
                                .findByCustomerNoAndProductCode(customerNo, productCode)
                                .orElseGet(() -> {
                                        Position p = new Position();
                                        p.setCustomerNo(customerNo);
                                        p.setProductCode(productCode);
                                        p.setShares(BigDecimal.ZERO);
                                        p.setFrozenShares(BigDecimal.ZERO);
                                        p.setCostAmount(BigDecimal.ZERO);
                                        p.setUpdatedAt(LocalDateTime.now());
                                        return p;
                                });
                position.setShares(position.getShares().add(shares));
                position.setCostAmount(position.getCostAmount().add(costAmount));
                position.setUpdatedAt(LocalDateTime.now());
                positionRepository.save(position);
        }

        private BigDecimal purchaseFeeRate(String productCode) {
                return productRepository.findByProductCode(productCode)
                                .map(Product::getPurchaseFeeRate)
                                .orElse(BigDecimal.ZERO);
        }

        private void failOrder(Order order, String taTag, String taSerialNo, String detail) {
                String from = order.getStatus();
                OrderStateMachine.assertTransition(from, "FAILED");
                order.setStatus("FAILED");
                order.setUpdatedAt(LocalDateTime.now());
                appendEvent(order, "FAIL", from, "FAILED", taTag, taSerialNo, detail);
        }

        private void transition(Order order, String to, String taTag, String taSerialNo, String detail) {
                String from = order.getStatus();
                OrderStateMachine.assertTransition(from, to);
                order.setStatus(to);
                order.setUpdatedAt(LocalDateTime.now());
                String eventType = switch (to) {
                        case "SUBMITTED" -> "SUBMIT";
                        case "TA_ACCEPTED" -> "TA_ACCEPT";
                        case "PENDING_QUEUE" -> "DELAY";
                        case "CONFIRMED" -> "CONFIRM";
                        case "SETTLED" -> "SETTLE";
                        case "REDEEMED" -> "REDEEM";
                        case "CANCELLED" -> "CANCEL";
                        default -> to;
                };
                appendEvent(order, eventType, from, to, taTag, taSerialNo, detail);
        }

        private void appendEvent(Order order, String eventType, String from, String to,
                        String taTag, String taSerialNo, String detail) {
                OrderEvent event = new OrderEvent();
                event.setOrderNo(order.getOrderNo());
                event.setEventType(eventType);
                event.setFromStatus(from);
                event.setToStatus(to);
                event.setTaTag(taTag);
                event.setTaSerialNo(taSerialNo);
                event.setDetail(detail);
                event.setCreatedAt(LocalDateTime.now());
                orderEventRepository.save(event);
        }
}

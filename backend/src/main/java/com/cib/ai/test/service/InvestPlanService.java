package com.cib.ai.test.service;

import com.cib.ai.test.model.InvestPlanCreateRequest;
import com.cib.ai.test.model.InvestPlanResponse;
import com.cib.ai.test.model.PurchaseRequest;
import com.cib.ai.test.repository.InvestPlanRepository;
import com.cib.ai.test.repository.ProductRepository;
import com.cib.ai.test.repository.entity.InvestPlan;
import com.cib.ai.test.repository.entity.Product;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 投资计划用例（spec 用户故事 22，功能 12 分级）：
 * 预约申购/定投 = 计划表 + @Scheduled 周期扫描到期触发下单（复用 purchase 全校验链与幂等）。
 * 预约触发失败（校验不过/资金不足）→ 计划 EXPIRED 作废（订单 FAILED 留痕）；
 * 定投触发失败 → 推进至下一期继续尝试（真实场景常见容错）。
 */
@Service
public class InvestPlanService {

    private static final Logger log = LoggerFactory.getLogger(InvestPlanService.class);
    private static final DateTimeFormatter PLAN_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final InvestPlanRepository investPlanRepository;
    private final ProductRepository productRepository;
    private final OrderService orderService;

    public InvestPlanService(InvestPlanRepository investPlanRepository,
            ProductRepository productRepository,
            OrderService orderService) {
        this.investPlanRepository = investPlanRepository;
        this.productRepository = productRepository;
        this.orderService = orderService;
    }

    /** 创建计划：RESERVE 预约申购 / REGULAR_INVEST 定投 */
    @Transactional
    public InvestPlanResponse create(String customerNo, InvestPlanCreateRequest request) {
        Product product = productRepository.findByProductCode(request.productCode())
                .orElseThrow(() -> new TradeException("PRODUCT_NOT_FOUND",
                        "产品不存在：" + request.productCode()));
        if (!"OPEN".equals(product.getStatus())) {
            throw new TradeException("PRODUCT_NOT_OPEN", "产品未开放交易：" + request.productCode());
        }
        if (request.triggerDate().isBefore(LocalDate.now())) {
            throw new TradeException("TRIGGER_DATE_INVALID", "触发日期不能早于今日");
        }
        boolean regular = "REGULAR_INVEST".equals(request.planType());
        if (regular && request.periodType() == null) {
            throw new TradeException("PERIOD_TYPE_REQUIRED", "定投计划必须指定周期（DAY/WEEK/MONTH）");
        }

        InvestPlan plan = new InvestPlan();
        plan.setPlanNo(generatePlanNo());
        plan.setCustomerNo(customerNo);
        plan.setProductCode(request.productCode());
        plan.setPlanType(request.planType());
        plan.setAmount(request.amount());
        plan.setTriggerDate(request.triggerDate());
        plan.setPeriodType(request.periodType());
        plan.setNextTriggerDate(request.triggerDate());
        plan.setStatus("ACTIVE");
        plan.setCreatedAt(LocalDateTime.now());
        return InvestPlanResponse.from(investPlanRepository.save(plan));
    }

    /** 本人计划列表 */
    @Transactional
    public List<InvestPlanResponse> list(String customerNo) {
        return investPlanRepository.findByCustomerNoOrderByCreatedAtDesc(customerNo).stream()
                .map(InvestPlanResponse::from)
                .toList();
    }

    /** 取消计划（仅本人 ACTIVE 计划可取消） */
    @Transactional
    public InvestPlanResponse cancel(String customerNo, String planNo) {
        InvestPlan plan = investPlanRepository.findByPlanNo(planNo)
                .filter(p -> customerNo.equals(p.getCustomerNo()))
                .orElseThrow(() -> new TradeException("PLAN_NOT_FOUND", "计划不存在：" + planNo));
        if (!"ACTIVE".equals(plan.getStatus())) {
            throw new TradeException("PLAN_NOT_CANCELLABLE",
                    "计划当前状态 " + plan.getStatus() + "，不可取消");
        }
        plan.setStatus("CANCELLED");
        return InvestPlanResponse.from(investPlanRepository.save(plan));
    }

    /**
     * 到期计划触发（由 @Scheduled Job 周期调用）：
     * 复用 OrderService.purchase 全校验链；clientRequestId = 计划号+触发日（Job 重扫幂等）。
     * 注意：本方法不加事务——每个计划经 orderService.purchase 独立事务提交，
     * 单计划失败不产生 rollback-only 污染、不阻断整批调度。
     */
    public void triggerDuePlans() {
        LocalDate today = LocalDate.now();
        List<InvestPlan> duePlans = investPlanRepository.findByStatus("ACTIVE").stream()
                .filter(p -> p.getNextTriggerDate() != null && !p.getNextTriggerDate().isAfter(today))
                .toList();
        for (InvestPlan plan : duePlans) {
            try {
                PurchaseOutcome outcome = orderService.purchase(plan.getCustomerNo(),
                        new PurchaseRequest(plan.getProductCode(), plan.getAmount(), false,
                                "PLAN-" + plan.getPlanNo() + "-" + plan.getNextTriggerDate()));
                if (outcome.errorCode() == null) {
                    log.info("计划触发下单成功：{}，订单 {}", plan.getPlanNo(),
                            outcome.order().getOrderNo());
                } else {
                    log.warn("计划触发下单失败：{}，原因：{}", plan.getPlanNo(), outcome.errorMessage());
                }
                advancePlan(plan, outcome.errorCode() == null);
            } catch (Exception e) {
                // 单计划触发异常不阻断批量调度
                log.error("计划触发异常：{}", plan.getPlanNo(), e);
                advancePlan(plan, false);
            }
        }
    }

    /** 触发后推进计划状态：预约→FINISHED/EXPIRED；定投→推进下一期（失败下期再试） */
    private void advancePlan(InvestPlan plan, boolean success) {
        if ("RESERVE".equals(plan.getPlanType())) {
            plan.setStatus(success ? "FINISHED" : "EXPIRED");
        } else {
            plan.setNextTriggerDate(nextDate(plan.getNextTriggerDate(), plan.getPeriodType()));
        }
        investPlanRepository.save(plan);
    }

    private LocalDate nextDate(LocalDate current, String periodType) {
        return switch (periodType) {
            case "DAY" -> current.plusDays(1);
            case "WEEK" -> current.plusWeeks(1);
            default -> current.plusMonths(1);
        };
    }

    private String generatePlanNo() {
        return "PLAN" + LocalDateTime.now().format(PLAN_NO_FORMATTER)
                + ThreadLocalRandom.current().nextInt(100, 999);
    }
}

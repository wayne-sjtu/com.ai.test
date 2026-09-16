package com.ai.test.controller;

import com.ai.test.model.InvestPlanCreateRequest;
import com.ai.test.model.InvestPlanResponse;
import com.ai.test.service.InvestPlanService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端投资计划端点（spec 用户故事 22，设计文档 4.1 `/plans`）：
 * 预约申购/定投计划 CRUD；到期由 @Scheduled 自动触发下单。
 */
@RestController
@RequestMapping("/api/customer/plans")
public class InvestPlanController {

    private final InvestPlanService investPlanService;

    public InvestPlanController(InvestPlanService investPlanService) {
        this.investPlanService = investPlanService;
    }

    /** 创建计划：RESERVE 预约申购 / REGULAR_INVEST 定投 */
    @PostMapping
    public InvestPlanResponse create(@Valid @RequestBody InvestPlanCreateRequest request,
                                     HttpSession session) {
        return investPlanService.create(CustomerAccountController.customerNo(session), request);
    }

    /** 本人计划列表 */
    @GetMapping
    public Map<String, Object> list(HttpSession session) {
        List<InvestPlanResponse> plans =
                investPlanService.list(CustomerAccountController.customerNo(session));
        return Map.of("total", plans.size(), "plans", plans);
    }

    /** 取消计划（仅 ACTIVE 可取消） */
    @DeleteMapping("/{planNo}")
    public InvestPlanResponse cancel(@PathVariable String planNo, HttpSession session) {
        return investPlanService.cancel(CustomerAccountController.customerNo(session), planNo);
    }
}

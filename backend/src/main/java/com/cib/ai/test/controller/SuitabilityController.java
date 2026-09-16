package com.cib.ai.test.controller;

import com.cib.ai.test.domain.SuitabilityResult;
import com.cib.ai.test.service.SuitabilityService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端适当性预检端点：申购表单提交前探测 CONFIRM/BLOCK，
 * 前端据此展示风险确认弹窗或拦截提示（引擎真实调用并留痕）。
 */
@RestController
@RequestMapping("/api/customer/suitability")
public class SuitabilityController {

    private final SuitabilityService suitabilityService;

    public SuitabilityController(SuitabilityService suitabilityService) {
        this.suitabilityService = suitabilityService;
    }

    @GetMapping
    public SuitabilityResult check(@RequestParam("productCode") String productCode,
                                   HttpSession session) {
        return suitabilityService.check(
                CustomerAccountController.customerNo(session), productCode, false);
    }
}

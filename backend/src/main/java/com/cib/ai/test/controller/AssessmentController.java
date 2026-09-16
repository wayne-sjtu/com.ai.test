package com.cib.ai.test.controller;

import com.cib.ai.test.model.AssessmentResponse;
import com.cib.ai.test.model.AssessmentSubmitRequest;
import com.cib.ai.test.service.RiskAssessmentService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** C 端风险测评端点（spec 功能 5）：提交测评 / 查最新等级与有效期 */
@RestController
@RequestMapping("/api/customer/assessment")
public class AssessmentController {

    private final RiskAssessmentService riskAssessmentService;

    public AssessmentController(RiskAssessmentService riskAssessmentService) {
        this.riskAssessmentService = riskAssessmentService;
    }

    @GetMapping
    public ResponseEntity<AssessmentResponse> latest(HttpSession session) {
        return riskAssessmentService
                .latest(CustomerAccountController.customerNo(session))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public AssessmentResponse submit(@Valid @RequestBody AssessmentSubmitRequest request, HttpSession session) {
        return riskAssessmentService.submit(
                CustomerAccountController.customerNo(session), request.answers());
    }
}

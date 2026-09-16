package com.ai.test.controller;

import com.ai.test.config.SessionKeys;
import com.ai.test.model.CustomerLoginRequest;
import com.ai.test.model.CustomerMeResponse;
import com.ai.test.service.AuthException;
import com.ai.test.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** C 端认证端点（ADR-0003/0004）：手机号密码登录，Session 标记客户身份 */
@RestController
@RequestMapping("/api/customer")
public class CustomerAuthController {

    private final AuthService authService;

    public CustomerAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public CustomerMeResponse login(@Valid @RequestBody CustomerLoginRequest request,
            HttpServletRequest httpRequest) {
        var customer = authService.customerLogin(
                request.mobile(), request.password(), request.channelCode());
        // Session 固定防护：废弃登录前会话，重建新会话（新 JSESSIONID）后再写入身份
        HttpSession old = httpRequest.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(SessionKeys.AUTH_TYPE, SessionKeys.TYPE_CUSTOMER);
        session.setAttribute(SessionKeys.CUSTOMER_NO, customer.getCustomerNo());
        return CustomerMeResponse.from(customer);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public CustomerMeResponse me(HttpSession session) {
        String customerNo = (String) session.getAttribute(SessionKeys.CUSTOMER_NO);
        return authService.currentCustomer(customerNo)
                .map(CustomerMeResponse::from)
                .orElseThrow(() -> new AuthException("AUTH_FAILED", "会话已失效，请重新登录"));
    }
}

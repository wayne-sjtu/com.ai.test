package com.cib.ai.test.controller;

import com.cib.ai.test.config.SessionKeys;
import com.cib.ai.test.model.AdminLoginRequest;
import com.cib.ai.test.model.AdminMeResponse;
import com.cib.ai.test.service.AuthException;
import com.cib.ai.test.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端认证端点（ADR-0003/0004）：独立账号登录，Session 标记管理端身份 */
@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AuthService authService;

    public AdminAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AdminMeResponse login(@Valid @RequestBody AdminLoginRequest request,
            HttpServletRequest httpRequest) {
        var operator = authService.adminLogin(request.username(), request.password());
        // Session 固定防护：废弃登录前会话，重建新会话（新 JSESSIONID）后再写入身份
        HttpSession old = httpRequest.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(SessionKeys.AUTH_TYPE, SessionKeys.TYPE_OPERATOR);
        session.setAttribute(SessionKeys.OPERATOR_USERNAME, operator.getUsername());
        return AdminMeResponse.from(operator);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AdminMeResponse me(HttpSession session) {
        String username = (String) session.getAttribute(SessionKeys.OPERATOR_USERNAME);
        return authService.currentOperator(username)
                .map(AdminMeResponse::from)
                .orElseThrow(() -> new AuthException("AUTH_FAILED", "会话已失效，请重新登录"));
    }
}

package com.ai.test.config;

import com.ai.test.model.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 双身份 Session 守卫（设计文档 11 章）：
 * /api/customer/** 仅客户 Session 可访问，/api/admin/** 仅管理端 Session，
 * 未登录 401，身份不符 403；登录端点在注册时排除。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public AuthInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String uri = request.getRequestURI();
        String requiredType = uri.startsWith("/api/admin/") ? SessionKeys.TYPE_OPERATOR
                : SessionKeys.TYPE_CUSTOMER;
        Object authType = request.getSession().getAttribute(SessionKeys.AUTH_TYPE);

        if (authType == null) {
            writeError(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录");
            return false;
        }
        if (!requiredType.equals(authType)) {
            writeError(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "无权访问该端点");
            return false;
        }
        return true;
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(new ApiError(code, message)));
    }
}

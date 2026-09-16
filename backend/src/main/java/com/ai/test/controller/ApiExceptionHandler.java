package com.ai.test.controller;

import com.ai.test.model.ApiError;
import com.ai.test.service.AccountOperationException;
import com.ai.test.service.AuthException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 统一错误响应：设计文档 4 章约定的 {code, message[, reasons]} 结构 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** 认证域异常：AUTH_FAILED → 401，渠道类错误 → 403 */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiError> handleAuth(AuthException e) {
        HttpStatus status = "AUTH_FAILED".equals(e.getCode())
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status).body(new ApiError(e.getCode(), e.getMessage()));
    }

    /** 账户域异常：解约拒绝 → 409（附原因列表），其余（签约前置/账户不存在）→ 400 */
    @ExceptionHandler(AccountOperationException.class)
    public ResponseEntity<ApiError> handleAccountOperation(AccountOperationException e) {
        HttpStatus status = "TERMINATE_REJECTED".equals(e.getCode())
                ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(new ApiError(e.getCode(), e.getMessage(), e.getReasons()));
    }

    /** 领域规则入参不合法（如测评答案格式）→ 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ApiError("PARAM_INVALID", e.getMessage()));
    }

    /** @Valid 参数校验失败 → 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse("参数不合法");
        return ResponseEntity.badRequest().body(new ApiError("PARAM_INVALID", message));
    }

    /** 并发冲突（乐观锁 / 幂等唯一约束竞争）→ 409，引导客户端换幂等键或重试 */
    @ExceptionHandler({ OptimisticLockException.class,
            ConcurrencyFailureException.class,
            DataIntegrityViolationException.class })
    public ResponseEntity<ApiError> handleConcurrency(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("CONCURRENT_CONFLICT", "操作存在并发冲突，请重试"));
    }
}

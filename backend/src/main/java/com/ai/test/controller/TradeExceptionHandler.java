package com.ai.test.controller;

import com.ai.test.model.ApiError;
import com.ai.test.service.TradeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 交易域异常：ORDER_NOT_FOUND / PRODUCT_NOT_FOUND / TICKET_NOT_FOUND / PLAN_NOT_FOUND
 * → 404；其余校验类 → 400
 */
@RestControllerAdvice
public class TradeExceptionHandler {

    @ExceptionHandler(TradeException.class)
    public ResponseEntity<ApiError> handleTrade(TradeException e) {
        HttpStatus status = "ORDER_NOT_FOUND".equals(e.getCode())
                || "PRODUCT_NOT_FOUND".equals(e.getCode())
                || "TICKET_NOT_FOUND".equals(e.getCode())
                || "PLAN_NOT_FOUND".equals(e.getCode())
                        ? HttpStatus.NOT_FOUND
                        : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ApiError(e.getCode(), e.getMessage()));
    }
}

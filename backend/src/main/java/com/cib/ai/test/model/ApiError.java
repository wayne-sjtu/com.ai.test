package com.cib.ai.test.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 统一错误响应（设计文档 4 章：错误返回 {code, message}；解约拒绝等场景附带 reasons） */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, List<String> reasons) {

    public ApiError(String code, String message) {
        this(code, message, null);
    }
}

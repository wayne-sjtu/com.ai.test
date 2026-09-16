package com.cib.ai.test.controller;

import com.cib.ai.test.model.DividendSettingRequest;
import com.cib.ai.test.service.DividendSettingService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端分红方式设置端点（spec 用户故事 23，设计文档 4.1 `/dividend-setting`）：
 * 现金分红/红利再投资，设置走 TA 报文登记（自营本行 TA / 代销外部 TA）。
 */
@RestController
@RequestMapping("/api/customer")
public class DividendSettingController {

    private final DividendSettingService dividendSettingService;

    public DividendSettingController(DividendSettingService dividendSettingService) {
        this.dividendSettingService = dividendSettingService;
    }

    /** 设置分红方式（CASH/REINVEST），走 TA 报文后落库 */
    @PutMapping("/dividend-setting")
    public Map<String, Object> set(@Valid @RequestBody DividendSettingRequest request,
                                   HttpSession session) {
        return dividendSettingService.set(CustomerAccountController.customerNo(session), request);
    }

    /** 本人全部分红方式设置 */
    @GetMapping("/dividend-settings")
    public List<Map<String, Object>> list(HttpSession session) {
        return dividendSettingService.list(CustomerAccountController.customerNo(session));
    }
}

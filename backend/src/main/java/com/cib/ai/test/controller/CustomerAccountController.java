package com.cib.ai.test.controller;

import com.cib.ai.test.config.SessionKeys;
import com.cib.ai.test.model.AccountResponse;
import com.cib.ai.test.model.CustomerMeResponse;
import com.cib.ai.test.service.CustomerProfileService;
import com.cib.ai.test.service.WealthAccountService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** C 端理财账户端点（spec 功能 4）：签约状态查询 / 签约 / 解约 / Mock 实名 */
@RestController
@RequestMapping("/api/customer")
public class CustomerAccountController {

    private final WealthAccountService wealthAccountService;
    private final CustomerProfileService customerProfileService;

    public CustomerAccountController(WealthAccountService wealthAccountService,
                                     CustomerProfileService customerProfileService) {
        this.wealthAccountService = wealthAccountService;
        this.customerProfileService = customerProfileService;
    }

    @GetMapping("/account")
    public AccountResponse account(HttpSession session) {
        return wealthAccountService.getAccount(customerNo(session));
    }

    @PostMapping("/account/sign")
    public AccountResponse sign(HttpSession session) {
        return wealthAccountService.sign(customerNo(session));
    }

    @PostMapping("/account/terminate")
    public AccountResponse terminate(HttpSession session) {
        return wealthAccountService.terminate(customerNo(session));
    }

    /** Mock 实名认证（人脸+活体）：演示链路直接通过 */
    @PostMapping("/real-name/verify")
    public CustomerMeResponse verifyRealName(HttpSession session) {
        return CustomerMeResponse.from(customerProfileService.verifyRealName(customerNo(session)));
    }

    static String customerNo(HttpSession session) {
        return (String) session.getAttribute(SessionKeys.CUSTOMER_NO);
    }
}

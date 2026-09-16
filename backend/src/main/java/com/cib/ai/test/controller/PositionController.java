package com.cib.ai.test.controller;

import com.cib.ai.test.model.PositionViewResponse;
import com.cib.ai.test.service.PositionViewService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** C 端持仓端点（spec 用户故事 25/26）：统一持仓视图 + 估值汇总（自营/代销分区） */
@RestController
@RequestMapping("/api/customer/positions")
public class PositionController {

    private final PositionViewService positionViewService;

    public PositionController(PositionViewService positionViewService) {
        this.positionViewService = positionViewService;
    }

    @GetMapping
    public PositionViewResponse positions(HttpSession session) {
        return positionViewService.view(CustomerAccountController.customerNo(session));
    }
}

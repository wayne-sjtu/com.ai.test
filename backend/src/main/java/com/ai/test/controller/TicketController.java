package com.ai.test.controller;

import com.ai.test.model.TicketResponse;
import com.ai.test.model.TicketSubmitRequest;
import com.ai.test.service.TicketService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端工单端点（spec 用户故事 29，设计文档 4.1 `/tickets`）：
 * 提交咨询/投诉工单（代销投诉同步发行机构）+ 本人工单查询。
 */
@RestController
@RequestMapping("/api/customer/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /** 提交工单：CONSULT 咨询 / COMPLAINT 投诉（代销产品投诉标记外部同步 PENDING） */
    @PostMapping
    public TicketResponse submit(@Valid @RequestBody TicketSubmitRequest request, HttpSession session) {
        return ticketService.submit(CustomerAccountController.customerNo(session), request);
    }

    /** 本人工单列表（含处理状态与答复） */
    @GetMapping
    public Map<String, Object> myTickets(HttpSession session) {
        List<TicketResponse> tickets = ticketService.myTickets(CustomerAccountController.customerNo(session));
        return Map.of("total", tickets.size(), "tickets", tickets);
    }
}

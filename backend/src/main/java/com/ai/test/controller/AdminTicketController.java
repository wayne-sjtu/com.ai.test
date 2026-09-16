package com.ai.test.controller;

import com.ai.test.config.SessionKeys;
import com.ai.test.model.TicketHandleRequest;
import com.ai.test.model.TicketResponse;
import com.ai.test.service.TicketService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端工单端点（spec 用户故事 31，设计文档 4.2 `/admin/tickets`）：
 * 受理（ACCEPT → PROCESSING）/ 关闭（CLOSE，答复必填，代销投诉置 SYNCED 完成发行机构闭环）；
 * 状态变化推送客户 TICKET_PROGRESS 站内消息。管理端 Session 由 AuthInterceptor 保证。
 */
@RestController
@RequestMapping("/api/admin/tickets")
public class AdminTicketController {

    private final TicketService ticketService;

    public AdminTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /** 工单列表：status（OPEN/PROCESSING/CLOSED）+ ticketType（CONSULT/COMPLAINT）组合筛选 */
    @GetMapping
    public List<TicketResponse> search(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "ticketType", required = false) String ticketType) {
        return ticketService.adminSearch(status, ticketType);
    }

    /** 处理工单：ACCEPT 受理 / CLOSE 关闭（reply 必填）；handler 取管理端 Session 账号 */
    @PutMapping("/{ticketNo}/handle")
    public TicketResponse handle(@PathVariable String ticketNo,
            @Valid @RequestBody TicketHandleRequest request,
            HttpSession session) {
        String handler = (String) session.getAttribute(SessionKeys.OPERATOR_USERNAME);
        return ticketService.handle(ticketNo, handler, request.action(), request.reply());
    }
}

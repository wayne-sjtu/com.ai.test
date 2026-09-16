package com.ai.test.controller;

import com.ai.test.model.ApiError;
import com.ai.test.model.MessageResponse;
import com.ai.test.repository.MessageRepository;
import com.ai.test.repository.entity.Message;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端消息中心（spec 用户故事 28，设计文档 4.1 `/messages`）：
 * 站内信查询（类型筛选/只看未读/未读数）+ 单条已读 + 全部已读。
 * 消息来源：净值发布推送、成交/受理通知、测评到期提醒、公告、投诉进度。
 */
@RestController
@RequestMapping("/api/customer/messages")
public class MessageController {

        private final MessageRepository messageRepository;

        public MessageController(MessageRepository messageRepository) {
                this.messageRepository = messageRepository;
        }

        /** 站内信列表：msgType 筛选（NAV/DEAL/EXPIRY/ANNOUNCEMENT/TICKET_PROGRESS）；unread=true 只看未读 */
        @GetMapping
        public Map<String, Object> messages(
                        @RequestParam(value = "msgType", required = false) String msgType,
                        @RequestParam(value = "unread", required = false) Boolean unread,
                        HttpSession session) {
                String customerNo = CustomerAccountController.customerNo(session);
                List<Message> all = messageRepository.findByCustomerNoOrderByCreatedAtDesc(customerNo);
                List<Message> filtered = all.stream()
                                .filter(m -> msgType == null || msgType.isBlank()
                                                || msgType.equals(m.getMsgType()))
                                .filter(m -> unread == null || !unread
                                                || !Boolean.TRUE.equals(m.getReadFlag()))
                                .toList();
                long unreadCount = all.stream()
                                .filter(m -> !Boolean.TRUE.equals(m.getReadFlag()))
                                .count();
                return Map.of(
                                "total", filtered.size(),
                                "unreadCount", unreadCount,
                                "messages", filtered.stream().map(MessageResponse::from).toList());
        }

        /** 标记已读（仅本人消息；不存在或非本人统一 404，不泄露他人消息存在性） */
        @PostMapping("/{id}/read")
        public ResponseEntity<Object> markRead(@PathVariable Long id, HttpSession session) {
                String customerNo = CustomerAccountController.customerNo(session);
                Message message = messageRepository.findById(id)
                                .filter(m -> customerNo.equals(m.getCustomerNo()))
                                .orElse(null);
                if (message == null) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(new ApiError("MESSAGE_NOT_FOUND", "消息不存在"));
                }
                message.setReadFlag(true);
                messageRepository.save(message);
                return ResponseEntity.ok(MessageResponse.from(message));
        }

        /** 全部已读（返回本次标记条数） */
        @PostMapping("/read-all")
        public Map<String, Object> markAllRead(HttpSession session) {
                String customerNo = CustomerAccountController.customerNo(session);
                List<Message> unreadList = messageRepository
                                .findByCustomerNoOrderByCreatedAtDesc(customerNo).stream()
                                .filter(m -> !Boolean.TRUE.equals(m.getReadFlag()))
                                .toList();
                unreadList.forEach(m -> m.setReadFlag(true));
                messageRepository.saveAll(unreadList);
                return Map.of("markedRead", unreadList.size());
        }
}

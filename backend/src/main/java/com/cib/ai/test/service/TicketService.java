package com.cib.ai.test.service;

import com.cib.ai.test.model.TicketResponse;
import com.cib.ai.test.model.TicketSubmitRequest;
import com.cib.ai.test.repository.MessageRepository;
import com.cib.ai.test.repository.ProductRepository;
import com.cib.ai.test.repository.TicketRepository;
import com.cib.ai.test.repository.entity.Message;
import com.cib.ai.test.repository.entity.Product;
import com.cib.ai.test.repository.entity.Ticket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客服工单用例（spec 用户故事 29/31）：
 * C 端提交咨询/投诉工单 —— 自营工单内部流转；代销投诉标记外部同步（PENDING），
 * 关闭时置 SYNCED 表示已同步发行机构完成闭环；
 * 管理端受理/关闭，状态变化推送客户 TICKET_PROGRESS 站内消息（spec 用户故事 28）。
 */
@Service
public class TicketService {

    private static final DateTimeFormatter TICKET_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TicketRepository ticketRepository;
    private final ProductRepository productRepository;
    private final MessageRepository messageRepository;

    public TicketService(TicketRepository ticketRepository,
            ProductRepository productRepository,
            MessageRepository messageRepository) {
        this.ticketRepository = ticketRepository;
        this.productRepository = productRepository;
        this.messageRepository = messageRepository;
    }

    /** C 端提交工单：代销产品投诉 → externalSyncStatus=PENDING（同步发行机构） */
    @Transactional
    public TicketResponse submit(String customerNo, TicketSubmitRequest request) {
        String productType = resolveProductType(request.productCode());

        Ticket ticket = new Ticket();
        ticket.setTicketNo(generateTicketNo());
        ticket.setCustomerNo(customerNo);
        ticket.setTicketType(request.ticketType());
        ticket.setProductCode(request.productCode());
        ticket.setContent(request.content());
        ticket.setStatus("OPEN");
        // 代销投诉需同步发行机构跟踪闭环；咨询与自营投诉内部流转
        boolean consignmentComplaint = "COMPLAINT".equals(request.ticketType())
                && "CONSIGNMENT".equals(productType);
        ticket.setExternalSyncStatus(consignmentComplaint ? "PENDING" : null);
        ticket.setHandler(null);
        ticket.setReply(null);
        LocalDateTime now = LocalDateTime.now();
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        return TicketResponse.from(ticketRepository.save(ticket), productType);
    }

    /** C 端本人工单列表（按提交时间倒序） */
    @Transactional
    public List<TicketResponse> myTickets(String customerNo) {
        Map<String, String> productTypes = productTypeIndex();
        return ticketRepository.findByCustomerNoOrderByCreatedAtDesc(customerNo).stream()
                .map(t -> TicketResponse.from(t, productTypes.get(t.getProductCode())))
                .toList();
    }

    /** 管理端工单列表：status/ticketType 组合筛选（条件下推数据库） */
    @Transactional
    public List<TicketResponse> adminSearch(String status, String ticketType) {
        Map<String, String> productTypes = productTypeIndex();
        return ticketRepository.search(normalize(status), normalize(ticketType)).stream()
                .map(t -> TicketResponse.from(t, productTypes.get(t.getProductCode())))
                .toList();
    }

    private static String normalize(String filter) {
        return (filter == null || filter.isBlank()) ? null : filter;
    }

    /**
     * 管理端受理/关闭：ACCEPT → PROCESSING；CLOSE → CLOSED（reply 必填）+ 代销投诉 SYNCED + 客户消息推送
     */
    @Transactional
    public TicketResponse handle(String ticketNo, String handler, String action, String reply) {
        Ticket ticket = ticketRepository.findByTicketNo(ticketNo)
                .orElseThrow(() -> new TradeException("TICKET_NOT_FOUND", "工单不存在：" + ticketNo));
        if ("CLOSED".equals(ticket.getStatus())) {
            throw new TradeException("TICKET_ALREADY_CLOSED", "工单已关闭，不可重复处理");
        }

        String previousStatus = ticket.getStatus();
        if ("ACCEPT".equals(action)) {
            ticket.setStatus("PROCESSING");
        } else {
            if (reply == null || reply.isBlank()) {
                throw new TradeException("REPLY_REQUIRED", "关闭工单必须填写处理答复");
            }
            ticket.setStatus("CLOSED");
            ticket.setReply(reply);
            // 代销投诉关闭 = 已同步发行机构并闭环
            if (ticket.getExternalSyncStatus() != null) {
                ticket.setExternalSyncStatus("SYNCED");
            }
        }
        ticket.setHandler(handler);
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        // 投诉进度消息推送（受理与关闭均通知，spec 用户故事 28 TICKET_PROGRESS）
        pushProgressMessage(ticket, previousStatus, reply);
        return TicketResponse.from(ticket,
                productTypeIndex().get(ticket.getProductCode()));
    }

    private void pushProgressMessage(Ticket ticket, String previousStatus, String reply) {
        Message message = new Message();
        message.setCustomerNo(ticket.getCustomerNo());
        message.setMsgType("TICKET_PROGRESS");
        message.setTitle("工单进度更新：" + ticket.getTicketNo());
        String progress = "CLOSED".equals(ticket.getStatus())
                ? "已关闭" + (reply != null ? "，答复：" + reply : "")
                : "客服已受理，正在处理中";
        message.setContent("您的" + ("COMPLAINT".equals(ticket.getTicketType()) ? "投诉" : "咨询")
                + "工单状态由 "
                + previousStatus + " 变更为 " + ticket.getStatus() + "，" + progress + "。");
        message.setReadFlag(false);
        message.setCreatedAt(LocalDateTime.now());
        messageRepository.save(message);
    }

    /** 校验关联产品存在（投诉代销产品时用于外部同步路由），返回产品类型 */
    private String resolveProductType(String productCode) {
        if (productCode == null || productCode.isBlank()) {
            return null;
        }
        Product product = productRepository.findByProductCode(productCode)
                .orElseThrow(() -> new TradeException("PRODUCT_NOT_FOUND",
                        "产品不存在：" + productCode));
        return product.getProductType();
    }

    private Map<String, String> productTypeIndex() {
        Map<String, String> types = new HashMap<>();
        for (Product p : productRepository.findAll()) {
            types.put(p.getProductCode(), p.getProductType());
        }
        return types;
    }

    private String generateTicketNo() {
        return "TK" + LocalDateTime.now().format(TICKET_NO_TIME)
                + ThreadLocalRandom.current().nextInt(100, 999);
    }
}

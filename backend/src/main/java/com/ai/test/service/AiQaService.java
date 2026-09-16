package com.ai.test.service;

import com.ai.test.llm.LlmClient;
import com.ai.test.model.AiQaRequest;
import com.ai.test.model.AiQaResponse;
import com.ai.test.repository.FaqRepository;
import com.ai.test.repository.TicketRepository;
import com.ai.test.repository.entity.Faq;
import com.ai.test.repository.entity.Ticket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 投教问答用例（spec 用户故事 30，功能 17）：
 * 敏感问题检测 → 直接转人工（自动创建 CONSULT 工单，保留问答记录）；
 * LLM 优先回答；LLM 不可用/拒答 → FAQ 关键词匹配降级；均未命中 → 建议转人工。
 * 所有回答强制附带：来源提示（LLM/FAQ）、风险声明、人工转接入口。
 */
@Service
public class AiQaService {

    /** 敏感问题关键词：涉及投诉/资金安全/争议，AI 不作答，直接转人工 */
    private static final List<String> SENSITIVE_KEYWORDS = List.of(
            "投诉", "亏损了怎么办", "被骗", "诈骗", "账户被盗", "赔偿", "冻结了", "无法赎回");

    private static final String RISK_DISCLAIMER =
            "以上内容由 AI 投教助手生成，仅供参考，不构成投资建议；理财非存款，产品有风险，投资须谨慎。";

    private static final DateTimeFormatter TICKET_NO_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final LlmClient llmClient;
    private final FaqRepository faqRepository;
    private final TicketRepository ticketRepository;

    public AiQaService(LlmClient llmClient,
                       FaqRepository faqRepository,
                       TicketRepository ticketRepository) {
        this.llmClient = llmClient;
        this.faqRepository = faqRepository;
        this.ticketRepository = ticketRepository;
    }

    @Transactional
    public AiQaResponse ask(String customerNo, AiQaRequest request) {
        String question = request.question();

        // 1. 敏感问题：AI 不作答，转人工并自动创建工单（保留问答记录）
        if (isSensitive(question)) {
            Ticket ticket = createHumanTicket(customerNo, question);
            return new AiQaResponse(
                    "您的问题涉及账户安全或争议事项，为保障您的权益，已为您转接人工客服"
                            + "（工单号 " + ticket.getTicketNo() + "），客服将尽快与您联系。",
                    "HUMAN", RISK_DISCLAIMER, true, ticket.getTicketNo(), null);
        }

        // 2. LLM 优先
        Optional<String> llmAnswer = llmClient.ask(question);
        if (llmAnswer.isPresent()) {
            return new AiQaResponse(llmAnswer.get(), "LLM", RISK_DISCLAIMER, false, null, null);
        }

        // 3. FAQ 关键词降级（LLM 不可用或拒答）
        Optional<Faq> faqHit = matchFaq(question);
        if (faqHit.isPresent()) {
            return new AiQaResponse(faqHit.get().getAnswer(), "FAQ",
                    RISK_DISCLAIMER + " 本回答来自投教知识库。", false, null,
                    faqHit.get().getQuestion());
        }

        // 4. 均未命中：建议转人工
        return new AiQaResponse(
                "抱歉，暂时无法回答您的问题。您可以换个方式提问，或点击\"转人工\"由客服为您解答。",
                "NONE", RISK_DISCLAIMER, true, null, null);
    }

    private boolean isSensitive(String question) {
        return SENSITIVE_KEYWORDS.stream().anyMatch(question::contains);
    }

    /** FAQ 关键词匹配：命中关键词数最多的条目优先 */
    private Optional<Faq> matchFaq(String question) {
        return faqRepository.findAll().stream()
                .map(f -> new Object() {
                    final Faq faq = f;
                    final long hits = List.of(f.getKeywords().split(",")).stream()
                            .map(String::trim)
                            .filter(k -> !k.isEmpty() && question.contains(k))
                            .count();
                })
                .filter(x -> x.hits > 0)
                .max(Comparator.comparingLong(x -> x.hits))
                .map(x -> x.faq);
    }

    /** 转人工工单：内容保留原始问答记录（AI 投教转人工标记） */
    private Ticket createHumanTicket(String customerNo, String question) {
        Ticket ticket = new Ticket();
        ticket.setTicketNo(generateTicketNo());
        ticket.setCustomerNo(customerNo);
        ticket.setTicketType("CONSULT");
        ticket.setProductCode(null);
        ticket.setContent("[AI 投教转人工] " + question);
        ticket.setStatus("OPEN");
        ticket.setExternalSyncStatus(null);
        LocalDateTime now = LocalDateTime.now();
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        return ticketRepository.save(ticket);
    }

    private String generateTicketNo() {
        return "TK" + LocalDateTime.now().format(TICKET_NO_TIME)
                + ThreadLocalRandom.current().nextInt(100, 999);
    }
}

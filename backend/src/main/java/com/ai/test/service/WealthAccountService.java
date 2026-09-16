package com.ai.test.service;

import com.ai.test.model.AccountResponse;
import com.ai.test.repository.CapitalAccountRepository;
import com.ai.test.repository.CustomerRepository;
import com.ai.test.repository.OrderRepository;
import com.ai.test.repository.PositionRepository;
import com.ai.test.repository.SignDocumentRepository;
import com.ai.test.repository.TradingPermissionRepository;
import com.ai.test.repository.WealthAccountRepository;
import com.ai.test.repository.entity.Customer;
import com.ai.test.repository.entity.TradingPermission;
import com.ai.test.repository.entity.WealthAccount;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 理财账户用例（spec 功能 4：签约状态机 + 双权限 + 解约三重校验）。
 * 赛题红线：签约前置任一失败即终止；解约存在持仓/在途/未结清费用时拒绝并返回原因列表。
 */
@Service
public class WealthAccountService {

    /** 签约前必须完成签署的四类适当性档案文件 */
    public static final Set<String> REQUIRED_DOC_TYPES = Set.of(
            "RIGHTS_NOTICE", "RISK_DISCLOSURE", "PRODUCT_AGREEMENT", "CONSIGNMENT_DISCLOSURE");

    /** 在途订单状态（非终态） */
    private static final Set<String> IN_FLIGHT_STATUSES = Set.of("CREATED", "SUBMITTED", "TA_ACCEPTED",
            "PENDING_QUEUE");

    private static final DateTimeFormatter NO_FORMATTER = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private final WealthAccountRepository wealthAccountRepository;
    private final TradingPermissionRepository tradingPermissionRepository;
    private final CustomerRepository customerRepository;
    private final SignDocumentRepository signDocumentRepository;
    private final PositionRepository positionRepository;
    private final OrderRepository orderRepository;
    private final CapitalAccountRepository capitalAccountRepository;

    public WealthAccountService(WealthAccountRepository wealthAccountRepository,
            TradingPermissionRepository tradingPermissionRepository,
            CustomerRepository customerRepository,
            SignDocumentRepository signDocumentRepository,
            PositionRepository positionRepository,
            OrderRepository orderRepository,
            CapitalAccountRepository capitalAccountRepository) {
        this.wealthAccountRepository = wealthAccountRepository;
        this.tradingPermissionRepository = tradingPermissionRepository;
        this.customerRepository = customerRepository;
        this.signDocumentRepository = signDocumentRepository;
        this.positionRepository = positionRepository;
        this.orderRepository = orderRepository;
        this.capitalAccountRepository = capitalAccountRepository;
    }

    /** 账户与权限状态：未签约返回 UNSIGNED 视图 */
    public AccountResponse getAccount(String customerNo) {
        return wealthAccountRepository.findByCustomerNo(customerNo)
                .map(account -> AccountResponse.from(
                        account, tradingPermissionRepository.findByAccountId(account.getId())))
                .orElseGet(AccountResponse::unsigned);
    }

    /** 签约：校验实名/尽调/四类文件签署，创建账户并开通自营+代销双权限 */
    @Transactional
    public AccountResponse sign(String customerNo) {
        Customer customer = customerRepository.findByCustomerNo(customerNo)
                .orElseThrow(() -> new AccountOperationException("CUSTOMER_NOT_FOUND", "客户不存在"));
        wealthAccountRepository.findByCustomerNo(customerNo).ifPresent(a -> {
            throw new AccountOperationException("SIGN_PRECONDITION_FAILED",
                    "签约前置校验未通过", List.of("已存在理财账户（状态 " + a.getStatus() + "），不可重复签约"));
        });

        List<String> reasons = new ArrayList<>();
        if (!"VERIFIED".equals(customer.getRealNameStatus())) {
            reasons.add("实名认证未完成");
        }
        if (!"PASSED".equals(customer.getKycStatus())) {
            reasons.add("客户尽调未通过");
        }
        Set<String> signedTypes = signDocumentRepository.findByCustomerNo(customerNo).stream()
                .map(d -> d.getDocType())
                .collect(Collectors.toSet());
        for (String required : REQUIRED_DOC_TYPES) {
            if (!signedTypes.contains(required)) {
                reasons.add("文件未签署：" + required);
            }
        }
        if (!reasons.isEmpty()) {
            throw new AccountOperationException("SIGN_PRECONDITION_FAILED", "签约前置校验未通过", reasons);
        }

        String serial = LocalDateTime.now().format(NO_FORMATTER)
                + ThreadLocalRandom.current().nextInt(10, 99);
        LocalDateTime now = LocalDateTime.now();
        WealthAccount account = new WealthAccount();
        account.setAccountNo("WA" + serial);
        account.setCustomerNo(customerNo);
        account.setStatus("SIGNED");
        account.setTaAccountNo("TA-INTA-" + serial);
        account.setSignedAt(now);
        account.setCreatedAt(now);
        account = wealthAccountRepository.save(account);

        for (String type : List.of("PROPRIETARY", "CONSIGNMENT")) {
            TradingPermission permission = new TradingPermission();
            permission.setAccountId(account.getId());
            permission.setPermissionType(type);
            permission.setStatus("OPEN");
            permission.setOpenedAt(now);
            permission.setCreatedAt(now);
            tradingPermissionRepository.save(permission);
        }

        // 补资金账户（余额 0，幂等：已存在则跳过）：否则新签约客户无资金账户，无法交易
        if (capitalAccountRepository.findByCustomerNo(customerNo).isEmpty()) {
            com.ai.test.repository.entity.CapitalAccount capitalAccount = new com.ai.test.repository.entity.CapitalAccount();
            capitalAccount.setCustomerNo(customerNo);
            capitalAccount.setAvailableBalance(java.math.BigDecimal.ZERO);
            capitalAccount.setFrozenBalance(java.math.BigDecimal.ZERO);
            capitalAccount.setCreatedAt(now);
            capitalAccountRepository.save(capitalAccount);
        }

        return getAccount(customerNo);
    }

    /** 解约：存在持仓/在途订单/冻结资金时拒绝并返回全部原因；通过则账户 TERMINATED、权限 CLOSED */
    @Transactional
    public AccountResponse terminate(String customerNo) {
        WealthAccount account = wealthAccountRepository.findByCustomerNo(customerNo)
                .orElseThrow(() -> new AccountOperationException("ACCOUNT_NOT_FOUND", "未找到理财账户"));
        if (!"SIGNED".equals(account.getStatus())) {
            throw new AccountOperationException("ACCOUNT_NOT_FOUND",
                    "账户当前状态为 " + account.getStatus() + "，不可解约");
        }

        List<String> reasons = new ArrayList<>();
        List<com.ai.test.repository.entity.Position> positions = positionRepository.findByCustomerNo(customerNo);
        long holdingCount = positions.stream()
                .filter(p -> p.getShares().signum() > 0)
                .count();
        if (holdingCount > 0) {
            reasons.add("持有 " + holdingCount + " 只产品份额未赎回");
        }
        long inFlight = orderRepository.findByCustomerNo(customerNo).stream()
                .filter(o -> IN_FLIGHT_STATUSES.contains(o.getStatus()))
                .count();
        if (inFlight > 0) {
            reasons.add("存在 " + inFlight + " 笔在途订单未完成");
        }
        capitalAccountRepository.findByCustomerNo(customerNo).ifPresent(c -> {
            if (c.getFrozenBalance().signum() > 0) {
                reasons.add("存在冻结资金 " + c.getFrozenBalance().toPlainString() + " 元未结清");
            }
        });
        if (!reasons.isEmpty()) {
            throw new AccountOperationException("TERMINATE_REJECTED", "存在未了结事项，禁止解约", reasons);
        }

        account.setStatus("TERMINATED");
        account.setTerminatedAt(LocalDateTime.now());
        tradingPermissionRepository.findByAccountId(account.getId())
                .forEach(p -> p.setStatus("CLOSED"));
        return getAccount(customerNo);
    }
}

package com.cib.ai.test.service;

import com.cib.ai.test.repository.CapitalAccountRepository;
import com.cib.ai.test.repository.CapitalFlowRepository;
import com.cib.ai.test.repository.entity.CapitalAccount;
import com.cib.ai.test.repository.entity.CapitalFlow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 资金账户用例（ADR-0006 简化账务）：
 * 可用/冻结余额 + 幂等资金流水（订单号 + 动作类型唯一）。
 * 动作：FREEZE 冻结 / DEDUCT 扣划 / UNFREEZE 解冻 / RETURN 回款。
 */
@Service
public class CapitalService {

    private final CapitalAccountRepository capitalAccountRepository;
    private final CapitalFlowRepository capitalFlowRepository;

    public CapitalService(CapitalAccountRepository capitalAccountRepository,
            CapitalFlowRepository capitalFlowRepository) {
        this.capitalAccountRepository = capitalAccountRepository;
        this.capitalFlowRepository = capitalFlowRepository;
    }

    /** 冻结：可用 → 冻结（申购受理时占用资金）；幂等，余额不足抛 TradeException */
    @Transactional
    public void freeze(String customerNo, String orderNo, BigDecimal amount) {
        CapitalAccount account = requireAccount(customerNo);
        if (capitalFlowRepository.findByOrderNoAndActionType(orderNo, "FREEZE").isPresent()) {
            return; // 幂等：同一订单不重复冻结
        }
        if (account.getAvailableBalance().compareTo(amount) < 0) {
            throw new TradeException("INSUFFICIENT_BALANCE",
                    "可用余额不足：当前可用 " + account.getAvailableBalance().toPlainString()
                            + " 元，需 " + amount.toPlainString() + " 元");
        }
        account.setAvailableBalance(account.getAvailableBalance().subtract(amount));
        account.setFrozenBalance(account.getFrozenBalance().add(amount));
        writeFlow(orderNo, customerNo, "FREEZE", amount, account.getAvailableBalance());
    }

    /** 扣划：冻结余额中划出（TA 确认后资金真正付出）；幂等 */
    @Transactional
    public void deduct(String customerNo, String orderNo, BigDecimal amount) {
        CapitalAccount account = requireAccount(customerNo);
        if (capitalFlowRepository.findByOrderNoAndActionType(orderNo, "DEDUCT").isPresent()) {
            return;
        }
        account.setFrozenBalance(account.getFrozenBalance().subtract(amount));
        writeFlow(orderNo, customerNo, "DEDUCT", amount, account.getAvailableBalance());
    }

    /** 解冻：冻结 → 可用（撤单 / TA 拒绝时释放）；幂等 */
    @Transactional
    public void unfreeze(String customerNo, String orderNo, BigDecimal amount) {
        CapitalAccount account = requireAccount(customerNo);
        if (capitalFlowRepository.findByOrderNoAndActionType(orderNo, "UNFREEZE").isPresent()) {
            return;
        }
        account.setFrozenBalance(account.getFrozenBalance().subtract(amount));
        account.setAvailableBalance(account.getAvailableBalance().add(amount));
        writeFlow(orderNo, customerNo, "UNFREEZE", amount, account.getAvailableBalance());
    }

    /** 回款：赎回确认后资金回到可用（redeem 链路用）；幂等 */
    @Transactional
    public void returnCash(String customerNo, String orderNo, BigDecimal amount) {
        CapitalAccount account = requireAccount(customerNo);
        if (capitalFlowRepository.findByOrderNoAndActionType(orderNo, "RETURN").isPresent()) {
            return;
        }
        account.setAvailableBalance(account.getAvailableBalance().add(amount));
        writeFlow(orderNo, customerNo, "RETURN", amount, account.getAvailableBalance());
    }

    private CapitalAccount requireAccount(String customerNo) {
        return capitalAccountRepository.findByCustomerNoForUpdate(customerNo)
                .orElseThrow(() -> new TradeException("CAPITAL_ACCOUNT_NOT_FOUND", "资金账户不存在"));
    }

    private void writeFlow(String orderNo, String customerNo, String action,
            BigDecimal amount, BigDecimal balanceAfter) {
        CapitalFlow flow = new CapitalFlow();
        flow.setOrderNo(orderNo);
        flow.setCustomerNo(customerNo);
        flow.setActionType(action);
        flow.setAmount(amount);
        flow.setBalanceAfter(balanceAfter);
        flow.setCreatedAt(LocalDateTime.now());
        capitalFlowRepository.save(flow);
    }
}

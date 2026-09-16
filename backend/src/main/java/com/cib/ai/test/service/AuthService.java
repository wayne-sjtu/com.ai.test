package com.cib.ai.test.service;

import com.cib.ai.test.repository.ChannelRepository;
import com.cib.ai.test.repository.CustomerRepository;
import com.cib.ai.test.repository.OperatorRepository;
import com.cib.ai.test.repository.entity.Channel;
import com.cib.ai.test.repository.entity.Customer;
import com.cib.ai.test.repository.entity.Operator;
import java.util.Optional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 认证用例（ADR-0003 双身份体系 / ADR-0004 Session）：
 * C 端手机号密码登录 + 渠道校验；管理端独立登录。
 */
@Service
public class AuthService {

    private static final String CHANNEL_ACTIVE = "ACTIVE";

    private final CustomerRepository customerRepository;
    private final OperatorRepository operatorRepository;
    private final ChannelRepository channelRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(CustomerRepository customerRepository,
                       OperatorRepository operatorRepository,
                       ChannelRepository channelRepository) {
        this.customerRepository = customerRepository;
        this.operatorRepository = operatorRepository;
        this.channelRepository = channelRepository;
    }

    /**
     * C 端登录：渠道须存在且启用，再校验手机号密码（BCrypt）。
     * 渠道不通过直接终止，不进入密码校验。
     */
    public Customer customerLogin(String mobile, String password, String channelCode) {
        validateChannel(channelCode);
        Customer customer = customerRepository.findByMobile(mobile)
                .orElseThrow(() -> AuthException.badCredentials("手机号或密码错误"));
        if (!passwordEncoder.matches(password, customer.getPasswordHash())) {
            throw AuthException.badCredentials("手机号或密码错误");
        }
        return customer;
    }

    /** 管理端登录：独立身份体系，用户名 + BCrypt 密码校验 */
    public Operator adminLogin(String username, String password) {
        Operator operator = operatorRepository.findByUsername(username)
                .orElseThrow(() -> AuthException.badCredentials("用户名或密码错误"));
        if (!passwordEncoder.matches(password, operator.getPasswordHash())) {
            throw AuthException.badCredentials("用户名或密码错误");
        }
        return operator;
    }

    /** 会话中的客户编号 → 客户实体（/me 查询用） */
    public Optional<Customer> currentCustomer(String customerNo) {
        return customerRepository.findByCustomerNo(customerNo);
    }

    /** 会话中的管理端用户名 → 用户实体（/me 查询用） */
    public Optional<Operator> currentOperator(String username) {
        return operatorRepository.findByUsername(username);
    }

    private void validateChannel(String channelCode) {
        if (!StringUtils.hasText(channelCode)) {
            throw new AuthException("CHANNEL_INVALID", "渠道不存在");
        }
        Channel channel = channelRepository.findByChannelCode(channelCode)
                .orElseThrow(() -> new AuthException("CHANNEL_INVALID", "渠道不存在"));
        if (!CHANNEL_ACTIVE.equals(channel.getStatus())) {
            throw new AuthException("CHANNEL_SUSPENDED", "渠道已停用");
        }
    }
}

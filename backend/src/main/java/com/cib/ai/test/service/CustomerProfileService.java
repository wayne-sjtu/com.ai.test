package com.cib.ai.test.service;

import com.cib.ai.test.repository.CustomerRepository;
import com.cib.ai.test.repository.entity.Customer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 客户资料用例：实名认证 Mock（人脸+活体直接通过，满足演示链路） */
@Service
public class CustomerProfileService {

    private final CustomerRepository customerRepository;

    public CustomerProfileService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /** Mock 实名认证：人脸+活体校验通过后置 VERIFIED，尽调同步 PASSED */
    @Transactional
    public Customer verifyRealName(String customerNo) {
        Customer customer = customerRepository.findByCustomerNo(customerNo)
                .orElseThrow(() -> new AccountOperationException("CUSTOMER_NOT_FOUND", "客户不存在"));
        customer.setRealNameStatus("VERIFIED");
        customer.setKycStatus("PASSED");
        return customer;
    }
}

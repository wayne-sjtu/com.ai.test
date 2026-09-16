package com.cib.ai.test.repository;

import com.cib.ai.test.repository.entity.Customer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 客户数据访问（C 端身份体系，ADR-0003） */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByMobile(String mobile);

    Optional<Customer> findByCustomerNo(String customerNo);

    /** 按渠道统计注册客户数（管理端渠道维护参考） */
    long countByChannelCode(String channelCode);
}

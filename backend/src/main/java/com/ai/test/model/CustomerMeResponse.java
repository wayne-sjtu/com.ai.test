package com.ai.test.model;

import com.ai.test.repository.entity.Customer;

/** C 端会话信息（登录响应与 /me 共用；不暴露 passwordHash） */
public record CustomerMeResponse(
        String customerNo,
        String name,
        String mobile,
        String realNameStatus,
        String kycStatus,
        String channelCode) {

    public static CustomerMeResponse from(Customer customer) {
        return new CustomerMeResponse(
                customer.getCustomerNo(),
                customer.getName(),
                customer.getMobile(),
                customer.getRealNameStatus(),
                customer.getKycStatus(),
                customer.getChannelCode());
    }
}

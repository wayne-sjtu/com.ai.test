package com.ai.test.service;

import com.ai.test.model.ChannelResponse;
import com.ai.test.model.ChannelResponse.ChannelListResponse;
import com.ai.test.repository.ChannelRepository;
import com.ai.test.repository.CustomerRepository;
import com.ai.test.repository.entity.Channel;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 渠道管理用例（spec 功能 2：登记和维护渠道信息——类型、核心/非核心、状态）：
 * 渠道数据驱动差异化（登录校验渠道码 + 状态），仅 PC Web 真实实现，其余渠道 Mock 登记。
 */
@Service
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final CustomerRepository customerRepository;

    public ChannelService(ChannelRepository channelRepository,
                          CustomerRepository customerRepository) {
        this.channelRepository = channelRepository;
        this.customerRepository = customerRepository;
    }

    public ChannelListResponse list() {
        List<ChannelResponse> channels = channelRepository.findAll().stream()
                .sorted((a, b) -> Boolean.compare(!a.getCoreFlag(), !b.getCoreFlag()))
                .map(c -> ChannelResponse.from(c,
                        customerRepository.countByChannelCode(c.getChannelCode())))
                .toList();
        return new ChannelListResponse(channels);
    }

    /** 维护渠道状态/核心标记（停用后该渠道登录即被拒绝，渠道码不可变更） */
    @Transactional
    public ChannelResponse update(String channelCode, String status, Boolean coreFlag) {
        Channel channel = channelRepository.findByChannelCode(channelCode)
                .orElseThrow(() -> new TradeException("CHANNEL_NOT_FOUND",
                        "渠道不存在：" + channelCode));
        if (status != null) {
            channel.setStatus(status);
        }
        if (coreFlag != null) {
            channel.setCoreFlag(coreFlag);
        }
        return ChannelResponse.from(channel,
                customerRepository.countByChannelCode(channelCode));
    }
}

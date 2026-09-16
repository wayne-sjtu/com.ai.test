package com.ai.test.model;

import com.ai.test.repository.entity.Channel;
import java.time.LocalDateTime;
import java.util.List;

/** 渠道信息响应（管理端维护，spec 功能 2：类型/核心标记/状态） */
public record ChannelResponse(
        String channelCode,
        String channelName,
        String channelType,
        Boolean coreFlag,
        String status,
        LocalDateTime createdAt,
        /** 该渠道注册客户数（渠道差异化管理参考） */
        long customerCount) {

    public static ChannelResponse from(Channel channel, long customerCount) {
        return new ChannelResponse(
                channel.getChannelCode(),
                channel.getChannelName(),
                channel.getChannelType(),
                channel.getCoreFlag(),
                channel.getStatus(),
                channel.getCreatedAt(),
                customerCount);
    }

    public record ChannelListResponse(List<ChannelResponse> channels) {
    }
}

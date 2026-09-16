package com.cib.ai.test.controller;

import com.cib.ai.test.model.ChannelResponse;
import com.cib.ai.test.model.ChannelResponse.ChannelListResponse;
import com.cib.ai.test.model.ChannelUpdateRequest;
import com.cib.ai.test.service.ChannelService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端渠道维护端点（spec 功能 2：渠道类型、核心/非核心、状态的登记维护）。
 * 管理端身份由 AuthInterceptor 按 /api/admin/** 前缀校验。
 */
@RestController
@RequestMapping("/api/admin/channels")
public class AdminChannelController {

    private final ChannelService channelService;

    public AdminChannelController(ChannelService channelService) {
        this.channelService = channelService;
    }

    /** 渠道列表（核心渠道在前，含各渠道客户数） */
    @GetMapping
    public ChannelListResponse list() {
        return channelService.list();
    }

    /** 维护渠道状态（ACTIVE/SUSPENDED）与核心标记 */
    @PutMapping("/{channelCode}")
    public ChannelResponse update(@PathVariable String channelCode,
            @Valid @RequestBody ChannelUpdateRequest request) {
        if (request.isEmpty()) {
            throw new com.cib.ai.test.service.TradeException(
                    "EMPTY_UPDATE", "至少提供 status 或 coreFlag 之一");
        }
        return channelService.update(channelCode, request.status(), request.coreFlag());
    }
}

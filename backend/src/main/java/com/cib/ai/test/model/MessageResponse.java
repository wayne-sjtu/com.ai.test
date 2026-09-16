package com.cib.ai.test.model;

import com.cib.ai.test.repository.entity.Message;
import java.time.LocalDateTime;

/** 站内消息视图（C 端消息中心） */
public record MessageResponse(
        Long id,
        String msgType,
        String title,
        String content,
        boolean readFlag,
        LocalDateTime createdAt) {

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getMsgType(),
                message.getTitle(),
                message.getContent(),
                Boolean.TRUE.equals(message.getReadFlag()),
                message.getCreatedAt());
    }
}

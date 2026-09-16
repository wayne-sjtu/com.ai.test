package com.cib.ai.test.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 启用 @Scheduled（巨额赎回 PENDING_QUEUE 延期确认 / 后续预约定投扫描） */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

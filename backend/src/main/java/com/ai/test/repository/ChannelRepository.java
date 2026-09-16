package com.ai.test.repository;

import com.ai.test.repository.entity.Channel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 渠道数据访问（数据驱动渠道差异化管理） */
public interface ChannelRepository extends JpaRepository<Channel, Long> {

    Optional<Channel> findByChannelCode(String channelCode);
}

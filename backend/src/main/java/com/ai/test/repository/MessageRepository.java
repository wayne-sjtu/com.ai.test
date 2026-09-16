package com.ai.test.repository;

import com.ai.test.repository.entity.Message;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 站内消息数据访问（净值/成交/到期/公告/投诉进度提醒） */
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByCustomerNoOrderByCreatedAtDesc(String customerNo);
}

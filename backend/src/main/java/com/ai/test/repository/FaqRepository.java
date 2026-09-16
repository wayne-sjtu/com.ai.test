package com.ai.test.repository;

import com.ai.test.repository.entity.Faq;
import org.springframework.data.jpa.repository.JpaRepository;

/** 投教 FAQ 数据访问（LLM 失败/拒答时的降级知识库） */
public interface FaqRepository extends JpaRepository<Faq, Long> {
}

package com.cib.ai.test.repository;

import com.cib.ai.test.repository.entity.Operator;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 管理端用户数据访问（独立身份体系，ADR-0003） */
public interface OperatorRepository extends JpaRepository<Operator, Long> {

    Optional<Operator> findByUsername(String username);
}

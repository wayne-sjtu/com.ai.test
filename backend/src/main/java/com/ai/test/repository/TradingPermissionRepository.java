package com.ai.test.repository;

import com.ai.test.repository.entity.TradingPermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 交易权限数据访问（每账户两条：PROPRIETARY / CONSIGNMENT） */
public interface TradingPermissionRepository extends JpaRepository<TradingPermission, Long> {

    List<TradingPermission> findByAccountId(Long accountId);
}

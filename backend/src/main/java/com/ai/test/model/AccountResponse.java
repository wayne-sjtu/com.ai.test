package com.ai.test.model;

import com.ai.test.repository.entity.TradingPermission;
import com.ai.test.repository.entity.WealthAccount;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 理财账户与权限状态。未签约时 accountNo 为 null、status 为 UNSIGNED，
 * permissions 为空列表（前端据此引导签约流程）。
 */
public record AccountResponse(
        String accountNo,
        String status,
        LocalDateTime signedAt,
        List<PermissionItem> permissions) {

    /** 未签约视图 */
    public static AccountResponse unsigned() {
        return new AccountResponse(null, "UNSIGNED", null, List.of());
    }

    public static AccountResponse from(WealthAccount account, List<TradingPermission> permissions) {
        return new AccountResponse(
                account.getAccountNo(),
                account.getStatus(),
                account.getSignedAt(),
                permissions.stream()
                        .map(p -> new PermissionItem(p.getPermissionType(), p.getStatus()))
                        .toList());
    }

    public record PermissionItem(String type, String status) {
    }
}

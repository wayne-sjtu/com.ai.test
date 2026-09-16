package com.cib.ai.test.controller;

import com.cib.ai.test.taclient.ExternalTaClient;
import com.cib.ai.test.taclient.TaClient;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查组件矩阵（spec 用户故事 34，设计文档 9.3）：
 * DB / 本行 TA（INTA）/ 外部 TA（EXTA）三组件状态，
 * 外部 TA 附带当前故障注入模式（演示差错矩阵用）。
 * 另一端点 /actuator/health 供 Compose healthcheck。
 */
@RestController
@RequestMapping("/api/admin/health")
public class HealthMatrixController {

    private final EntityManager em;
    private final TaClient internalTaClient;
    private final ExternalTaClient externalTaClient;

    public HealthMatrixController(EntityManager em,
            @Qualifier("inProcessInternalTaClient") TaClient internalTaClient,
            @Qualifier("inProcessExternalTaClient") ExternalTaClient externalTaClient) {
        this.em = em;
        this.internalTaClient = internalTaClient;
        this.externalTaClient = externalTaClient;
    }

    @GetMapping("/matrix")
    public Map<String, Object> matrix() {
        List<Map<String, Object>> components = new ArrayList<>();
        components.add(check("DB", "数据库", this::checkDb));
        components.add(check("INTERNAL_TA", "本行 TA", () -> internalTaClient.ping() ? "UP" : "DOWN"));
        Map<String, Object> externalTa = check("EXTERNAL_TA", "外部 TA", () -> externalTaClient.ping() ? "UP" : "DOWN");
        externalTa.put("faultMode", externalTaClient.getFaultMode().name());
        components.add(externalTa);

        boolean allUp = components.stream().allMatch(c -> "UP".equals(c.get("status")));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("overall", allUp ? "UP" : "DOWN");
        body.put("checkedAt", LocalDateTime.now());
        body.put("components", components);
        return body;
    }

    private String checkDb() {
        em.createNativeQuery("select 1").getSingleResult();
        return "UP";
    }

    private Map<String, Object> check(String name, String label, ComponentCheck check) {
        String status;
        String detail;
        try {
            status = check.check();
            detail = "OK";
        } catch (Exception e) {
            status = "DOWN";
            detail = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("name", name);
        component.put("label", label);
        component.put("status", status);
        component.put("detail", detail);
        return component;
    }

    @FunctionalInterface
    private interface ComponentCheck {
        String check();
    }
}

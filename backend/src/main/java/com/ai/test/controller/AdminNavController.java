package com.ai.test.controller;

import com.ai.test.model.NavPublishRequest;
import com.ai.test.repository.entity.ProductNav;
import com.ai.test.service.NavPublishService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端净值端点（spec 用户故事 33）：自营净值发布，C 端估值与走势数据来源 */
@RestController
@RequestMapping("/api/admin/nav")
public class AdminNavController {

    private final NavPublishService navPublishService;

    public AdminNavController(NavPublishService navPublishService) {
        this.navPublishService = navPublishService;
    }

    @PostMapping("/publish")
    public Map<String, Object> publish(@Valid @RequestBody NavPublishRequest request) {
        ProductNav nav = navPublishService.publish(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productCode", nav.getProductCode());
        body.put("navDate", nav.getNavDate());
        body.put("nav", nav.getNav());
        body.put("source", nav.getSource());
        body.put("publishedAt", LocalDateTime.now());
        return body;
    }
}

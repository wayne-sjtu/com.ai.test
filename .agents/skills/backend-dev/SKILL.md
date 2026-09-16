---
name: backend-dev
description: 在 backend/ 下新增或修改 Spring Boot 接口、服务层、配置。当任务涉及 Java 代码、Controller、Service、application.yml 时使用本技能。
---

# backend-dev — 后端开发技能

## 前置阅读

- 详细分层规范：`docs/reference/backend-conventions.md`（涉及 service/repository 分层时读取）
- 代码模板：`.agents/templates/controller.java.tmpl`、`service.java.tmpl`（新建类时参考，清单见 `.agents/templates/README.md`）

## 步骤

1. **定位**：先读 `backend/src/main/java/com/ai/test/` 下相关文件，理解现有结构。
2. **分层**：
   - Controller 只做参数校验与响应封装，业务逻辑下沉到 `service/`。
   - 包路径：`com.ai.test.controller` / `com.ai.test.service` 等。
3. **实现**：
   - REST 接口返回统一结构（如有统一响应类则复用，没有则先用简单类型，不擅自引入规范）。
   - 配置写入 `application.yml`，不要硬编码。
4. **验证**：
   ```bash
   ./scripts/verify.sh               # 一键验证（后端编译/测试）
   cd backend && ./mvnw spring-boot:run # 启动无报错
   curl http://localhost:8080/<path>    # 接口冒烟
   ```

## 禁止事项

- 不引入项目尚未使用的新依赖，除非任务明确要求。
- 不修改 `pom.xml` 版本号或 parent。
- 不生成无关的样板代码（如空的 DTO/BaseController）。

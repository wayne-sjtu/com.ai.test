---
name: testing
description: 编写/运行后端 JUnit 测试与前端验证。当任务要求写测试、跑测试或验证功能正确性时使用本技能。
---

# testing — 测试技能

## 后端测试

1. 测试类放 `backend/src/test/java/com/cib/ai/test/`，与被测类同包。
2. 使用 JUnit 5 + `spring-boot-starter-test`（已在依赖中），`@SpringBootTest` 用于集成测试。
3. 运行：
   ```bash
   cd backend && ./mvnw test
   ```

## 前端验证

当前项目无前端测试框架，验证方式：

```bash
cd frontend && npm run lint   # 静态检查
cd frontend && npm run build  # 类型检查 + 构建
cd frontend && npm run dev    # 手动功能验证
```

如用户要求引入 Vitest/RTL 等测试框架，先确认再动手。

## 原则

- 测试聚焦本次改动的行为，不追求覆盖率指标。
- 测试失败先区分：被测代码的 bug vs 测试本身写错。
- 不为通过测试而修改断言预期，除非确认是需求变更。

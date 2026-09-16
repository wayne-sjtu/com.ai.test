---
name: fullstack-api
description: 端到端交付一个新功能：后端接口 + 前端调用 + 联调验证。当任务要求“完成一个功能”且横跨 backend/ 与 frontend/ 时使用本技能。
---

# fullstack-api — 端到端 API 交付技能

## 流程

1. **确认契约**：先与用户对齐接口路径、方法、入参、响应结构（或从需求中推导并在回复中说明）。
2. **后端**：按 `backend-dev` 技能实现接口（见 `.agents/skills/backend-dev/SKILL.md`）。
3. **前端**：按 `frontend-dev` 技能实现调用与界面（见 `.agents/skills/frontend-dev/SKILL.md`）。
4. **联调**：
   ```bash
   # 终端 1
   cd backend && ./mvnw spring-boot:run
   # 终端 2
   cd frontend && npm run dev
   ```
   - 浏览器/curl 验证前端 → 后端全链路。
   - 检查浏览器控制台无报错、无跨域问题。

## 检查清单

- [ ] 后端编译、启动正常
- [ ] 前端 lint、build 通过
- [ ] 接口实际调用成功（不是只看代码）
- [ ] 错误情况（后端异常时）前端有合理表现

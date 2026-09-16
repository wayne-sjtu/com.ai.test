---
name: frontend-dev
description: 在 frontend/ 下新增或修改 React 组件、页面、样式、路由。当任务涉及 .tsx/.ts/.css 文件或 Vite 配置时使用本技能。
---

# frontend-dev — 前端开发技能

## 前置阅读

- 组件与样式规范：`docs/reference/frontend-conventions.md`（涉及组件拆分、命名时读取）
- 代码模板：`.agents/templates/Component.tsx.tmpl`、`api-service.ts.tmpl`（新建组件/接口封装时参考，清单见 `.agents/templates/README.md`）

## 步骤

1. **定位**：先读 `frontend/src/` 下相关文件（入口 `main.tsx`，根组件 `App.tsx`）。
2. **实现**：
   - 组件放 `src/` 下按功能分目录（如 `src/components/`、`src/pages/`），函数式组件 + TypeScript。
   - 调用后端接口默认 `http://localhost:8080`，注意配置 Vite 代理避免跨域。
   - 样式跟随现有方案（当前为 `App.css` / `index.css`），不擅自引入 UI 框架。
3. **验证**：
   ```bash
   ./scripts/verify.sh               # 一键验证（前端 lint/build）
   cd frontend && npm run dev        # 页面可访问、功能正常
   ```

## 禁止事项

- 不修改 `package.json` 依赖版本，除非任务明确要求。
- 不删除或改动 `dist/` 产物（构建生成物）。
- 不引入 state 管理库/路由库，除非任务明确要求。

---
name: code-review
description: 审查代码变更（git diff / 提交前自查 / 指定文件）。当用户要求 review、检查代码或提交前确认时使用本技能。
---

# code-review — 代码审查技能

## 流程

1. **获取变更**：
   ```bash
   git status
   git diff            # 未暂存
   git diff --cached   # 已暂存
   ```
2. **逐项检查**：
   - 正确性：逻辑、边界条件、空值处理
   - 一致性：是否遵循项目分层与命名规范（详见 `docs/reference/backend-conventions.md` 与 `docs/reference/frontend-conventions.md`）
   - 安全：SQL/命令注入、敏感信息硬编码、越权访问
   - 性能：明显的 N+1、重复计算、不必要的渲染
   - 可维护性：命名、重复代码、死代码
3. **输出格式**：按严重程度列出（🔴 必须修 / 🟡 建议修 / 🔵 可选），附文件与行号。

## 原则

- 只报告有依据的问题，引用具体代码说明。
- 区分“本次变更引入的问题”与“存量问题”，存量问题只提示不展开。
- 不借 review 之名做大规模重构建议。

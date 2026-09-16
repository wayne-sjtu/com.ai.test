# ADR-0002：Agent 工程化目录方案

- 状态：已接受
- 日期：2026-09-15

## 背景

项目需要在仓库内沉淀 Agent 协作规范，使 AI 助手（TRAE / Claude Code 等）行为可预期、可复用，且不污染正常代码目录。

## 决策

采用**三层按需加载**结构：

```
AGENTS.md               # 第 1 层：主入口，始终加载，保持精简
.agents/skills/         # 第 2 层：技能，任务匹配时加载对应 SKILL.md
docs/reference/         # 第 3 层：规范文档，按需读取
docs/context.md         # 项目上下文与当前状态
docs/decisions/         # ADR 架构决策记录
.agents/templates/      # 代码模板
scripts/                # 可执行脚本（verify.sh 一键验证）
```

- 技能文件使用 YAML frontmatter（name + description 描述触发场景），保留在 `.agents/skills/` 以支持工具自动发现
- 面向人的规范文档集中在根目录 `docs/` 下，与代码资产（templates）分离
- 任何规范新增优先写入 docs/reference/，避免主入口膨胀

## 理由

- 分层加载控制上下文成本：Agent 平时只背 AGENTS.md，任务来时再加载对应细节
- SKILL.md 的 frontmatter 描述可被工具自动发现与路由
- ADR 让 Agent 知道"为什么这样选"，避免推翻既有决策

## 后果

- 重大技术变更需同步更新 decisions/ 与 context.md
- 新技能出现时在 AGENTS.md 技能索引中登记

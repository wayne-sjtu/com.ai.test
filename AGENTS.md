# AGENTS.md — CIB AI Test 项目 Agent 指南

> 本文件是 Agent 的**主入口**，保持精简。详细规范放在 `docs/`（规范文档）与 `.agents/`（技能与模板）下，**按需加载**，不要一次性全部读取。

## 项目概览

全栈测试项目：

| 模块 | 目录 | 技术栈 |
|------|------|--------|
| 后端 | `backend/` | Java 17 · Spring Boot 3.3.5 · Maven |
| 前端 | `frontend/` | React 19 · TypeScript · Vite 8 · Oxlint |

## 目录结构

```
com.cib.ai.test/
├── AGENTS.md                 # 本文件：Agent 主入口（始终加载）
├── CONTEXT.md                # 领域词汇表（术语冲突时以此为准）
├── backend/                  # Spring Boot 后端
│   └── src/main/java/com/cib/ai/test/
│       ├── controller/       # REST 控制器
│       └── CibAiTestApplication.java
├── frontend/                 # React 前端
│   └── src/
├── docs/                     # 文档目录（面向人的规范，按需加载）
│   ├── context.md            # 项目上下文与当前状态（接手任务前读取）
│   ├── decisions/            # ADR 架构决策记录（改技术选型前必查）
│   ├── specs/                # 功能规格（ready-for-agent，实现任务的输入）
│   └── reference/            # 规范文档（仅在需要时读取）
│       ├── backend-conventions.md
│       ├── frontend-conventions.md
│       ├── commands.md
│       └── troubleshooting.md
├── .agents/
│   ├── skills/               # 技能目录（匹配到任务时加载对应 SKILL.md）
│   │   ├── backend-dev/      # 后端开发
│   │   ├── frontend-dev/     # 前端开发
│   │   ├── fullstack-api/    # 端到端 API 交付
│   │   ├── debugging/        # 调试排查
│   │   ├── testing/          # 测试
│   │   └── code-review/      # 代码审查
│   └── templates/            # 代码模板（新建类/组件时参考）
└── scripts/
    └── verify.sh             # 一键验证：后端编译/测试 + 前端 lint/build
```

## 常用命令

```bash
# 后端
cd backend && ./mvnw spring-boot:run        # 启动（端口 8080）
cd backend && ./mvnw clean package          # 构建
cd backend && ./mvnw test                   # 测试

# 前端
cd frontend && npm run dev                  # 开发服务器
cd frontend && npm run build                # 构建
cd frontend && npm run lint                 # Oxlint 检查

# 一键验证（Agent 改代码后必须运行）
./scripts/verify.sh                         # 后端编译/测试 + 前端 lint/build
./scripts/verify.sh --skip-tests            # 跳过后端测试
```

## 核心规则（必须遵守）

1. **后端**：包结构遵循 `com.cib.ai.test.<layer>`（controller / service / repository 等）；新接口放在 `controller/`，命名 `XxxController`。
2. **前端**：组件用 `.tsx` 函数式组件；提交前必须通过 `npm run lint` 和 `tsc -b`。
3. **通用**：改代码前先读目标文件；不做超出要求范围的修改；中文注释与提交信息。

## 按需加载策略

- **技能（Skills）**：当任务匹配某一技能时，先读取 `.agents/skills/<技能名>/SKILL.md` 再动手。
- **规范文档（Reference）**：`docs/reference/`，仅当任务涉及对应领域的详细规范/命令/排障时才读取，平时不要加载。
- **项目上下文**：接手不熟悉的任务前读 `docs/context.md` 了解当前状态；重大变更后同步更新它。
- **架构决策（ADR）**：修改技术选型、引入新依赖/框架前，先查 `docs/decisions/`（入口 `docs/decisions/README.md`）；做出新决策后新增一条 ADR。
- **代码模板**：新建类/组件时参考 `.agents/templates/` 下对应模板（清单见 `.agents/templates/README.md`），保持风格一致。
- 新增规范时优先写入 `docs/reference/` 对应文档，而不是撑大本文件。

## 技能索引

| 技能 | 触发场景 | 入口文件 |
|------|----------|----------|
| backend-dev | 新增/修改后端接口、服务、配置 | `.agents/skills/backend-dev/SKILL.md` |
| frontend-dev | 新增/修改页面、组件、样式 | `.agents/skills/frontend-dev/SKILL.md` |
| fullstack-api | 端到端交付一个新功能（前后端联动） | `.agents/skills/fullstack-api/SKILL.md` |
| debugging | 报错、启动失败、接口异常排查 | `.agents/skills/debugging/SKILL.md` |
| testing | 编写/运行后端测试、前端验证 | `.agents/skills/testing/SKILL.md` |
| code-review | 审查代码变更、提交前自查 | `.agents/skills/code-review/SKILL.md` |

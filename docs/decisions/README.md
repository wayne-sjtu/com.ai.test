# 架构决策记录（ADR）

> **按需加载**：修改技术选型、引入新依赖/框架前，先查这里——已有决策不要随意推翻。
>
> **记录规则**：做出新的重要技术决策后，新增一条 ADR，文件名 `NNNN-主题.md`（编号递增）。

## 索引

| 编号 | 决策 | 状态 |
|------|------|------|
| [0001](0001-tech-stack.md) | 技术栈选型：Spring Boot 3 + React 19 + Vite | 已接受 |
| [0002](0002-agent-engineering.md) | Agent 工程化目录方案：AGENTS.md + .agents/ + docs/ | 已接受 |
| [0003](0003-separate-identity-systems.md) | C 端客户与管理端用户两套独立身份体系 | 已接受 |
| [0004](0004-session-based-auth.md) | 统一认证采用服务端 Session（非 JWT） | 已接受 |
| [0005](0005-dual-ta-mock-containers.md) | 本行 TA 与外部 TA 各自独立 Mock 容器 | 已接受 |
| [0006](0006-simplified-accounting-model.md) | 简化账务模型（资金账户 + 幂等流水 + 份额持仓） | 已接受 |
| [0007](0007-mysql-8.md) | 数据库选型 MySQL 8 | 已接受 |

# ADR-0001：技术栈选型

- 状态：已接受
- 日期：2026-09-15

## 背景

项目为全栈测试/演示项目，需要一套主流、轻量、易于 AI 辅助开发验证的前后端技术栈。

## 决策

- **后端**：Java 17 + Spring Boot 3.3.5（spring-boot-starter-web），Maven 构建（mvnw wrapper）
- **前端**：React 19 + TypeScript + Vite 8 + Oxlint
- **不引入**：数据库、JPA、消息队列、状态管理库、路由库、UI 组件库

## 理由

- Spring Boot 与 React 是当前最主流组合，AI 代码生成质量高、社区资料全
- Vite 8 + Oxlint 工具链快，适合演示迭代
- 测试项目保持零持久化依赖，降低环境搭建成本

## 后果

- 涉及持久化的新需求需要新增 ADR 并与用户确认存储方案
- 前端复杂化（多页面）时需引入路由，届时补充 ADR

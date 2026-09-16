# ADR-0004：统一认证采用服务端 Session（非 JWT）

- 状态：已接受
- 日期：2026-09-15

## 背景

平台需要统一身份认证（功能 2），C 端客户与管理端用户为两套独立身份体系（ADR-0003）。候选：服务端 Session（Spring Session）、JWT 无状态、JWT + Refresh Token。

## 决策

采用服务端 Session（Spring Session），C 端与管理端各自登录端点，session 中携带身份类型。

## 理由

- 单体部署、Docker-Compose 单实例场景下 Session 最简单直接
- 注销/封禁即时生效（无需处理 JWT 黑名单）
- 与两套身份体系配合：登录端点区分 customer / operator，session 属性标记身份类型，越权访问在拦截层直接拒绝

## 会话存储

内存存储（非 JDBC/Redis）。取舍：重启丢失登录态可接受（评审重启后重新登录即可），换取零额外组件；不支持多实例水平扩展。

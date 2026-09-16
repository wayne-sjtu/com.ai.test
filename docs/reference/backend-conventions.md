# 后端规范（按需加载）

> 仅在涉及后端详细设计时读取本文件。

## 分层结构（参考 DDD）

采用**轻量 DDD 分层**，包结构参考 DDD 战术模式但保持简单（测试项目不引入事件总线、CQRS 等重武器）：

```
com.cib.ai.test
├── CibAiTestApplication.java   # 启动类
├── controller/                 # 接口适配层（用户接口层）：REST 端点
├── service/                    # 应用层：用例编排、事务边界
├── domain/                     # 领域层：实体、值对象、领域业务规则（按需创建）
├── repository/                 # 基础设施层：数据访问实现（按需创建）
├── model/                      # DTO / 入参出参对象（按需创建）
└── config/                     # 配置类（按需创建）
```

### DDD 核心规则

1. **依赖方向**：`controller → service → domain`，domain 层**不依赖**任何外层（无 Spring 注解、无框架类型），保持纯粹。
2. **分层职责**：
   - `controller/`：参数校验（`@Valid`）、HTTP 语义、响应封装；**不写业务逻辑**。
   - `service/`：一个方法对应一个用例，负责事务（`@Transactional`）与领域对象编排；**不写 SQL、不处理 HTTP 细节**。
   - `domain/`：业务规则写在实体/值对象上（如 `Order.canCancel()`），而非 Service 里堆 if-else。
   - `repository/`：只做持久化，返回领域对象；接口定义可放 domain，实现放 infrastructure。
3. **值对象**：无状态、不可变的建模优先用 `record`（Java 17）。
4. **DTO 与领域对象分离**：`model/` 下的 DTO 只为接口传输服务，不把实体直接暴露给前端。
5. **当前项目仅存在 `controller/`**，新层次按需创建，不预先搭建空目录。

## Java 常用规范

### 命名

| 类型 | 约定 | 示例 |
|------|------|------|
| 类/接口 | UpperCamelCase，类名名词 | `UserService`、`Order` |
| 方法 | lowerCamelCase，动词开头 | `cancelOrder()`、`findByStatus()` |
| 常量 | `UPPER_SNAKE_CASE` | `MAX_RETRY_TIMES` |
| 包 | 全小写单数 | `controller`、`domain` |
| 测试类 | 被测类名 + `Test` | `UserServiceTest` |

### 代码风格

- **方法不超过 50 行**，超出则拆分私有方法或下沉到 domain。
- **避免魔法值**：常量、枚举或配置项，不硬编码数字/字符串散落逻辑中。
- 优先使用 `Optional` 返回可空结果，而不是返回 `null` 或抛 NPE 风险。
- 集合参数/返回值不使用 `null`，空集合用 `List.of()` / `Collections.emptyList()`。
- 字符串判空用 `StringUtils.hasText()`（Spring 提供）或 `str == null || str.isBlank()`。
- **注释**：Javadoc 说明“为什么”而非“做了什么”；公共 API 必须有 Javadoc，私有方法逻辑自明则不加。
- **异常**：不用异常控制流程；业务异常语义明确（如 `IllegalArgumentException` 表参数错误），不自定义大而全的 `BusinessException` 塞所有场景（业务复杂时再引入）。

### 接口约定

- Controller 命名 `XxxController`，路径 `/api/` 前缀 + kebab-case 或复数资源名，如 `/api/orders`。
- REST 语义：GET 查询、POST 创建、PUT 全量更新、PATCH 部分更新、DELETE 删除。
- 参数校验放 Controller 层（`@Valid` + `jakarta.validation` 注解），校验失败交给框架处理。
- 分页/列表接口约定：`page`（从 0 或 1 全项目统一）、`size`、返回 `total`。

## 技术约定

- Java 17 语法特性可用（record、switch 表达式、text block、sealed class）。
- 依赖管理：只加 `spring-boot-starter-*` 官方 starter 或明确要求的库，版本由 parent 管理，不写 `<version>`。
- 配置一律走 `application.yml`，用 `@ConfigurationProperties`（优先）或 `@Value` 注入。
- 日志用 `@Slf4j`（Lombok 未引入前用 `LoggerFactory.getLogger`），占位符 `{}` 而非字符串拼接；**不打 System.out**。

## 响应与异常

- 简单接口直接返回领域对象/DTO；需要统一响应结构时先与用户确认结构。
- 异常处理：出现多处 try/catch 重复时再引入 `@RestControllerAdvice`，不提前做。

## 提交要求

- `./scripts/verify.sh` 通过（编译 + 测试）。
- 涉及接口的改动需启动服务并 curl 冒烟验证。

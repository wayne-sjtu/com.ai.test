# 排障手册（按需加载）

> 仅在排查问题卡住时读取本文件。

## 后端

| 症状 | 排查方向 |
|------|----------|
| 启动报端口占用 | `lsof -i :8080`，结束占用进程或改 `application.yml` 端口 |
| 依赖下载失败 | 检查 Maven 镜像/网络；可 `rm -rf ~/.m2/repository/<坐标>` 后重试 |
| 404 | 确认 Controller 路径拼写、组件扫描包路径（`com.ai.test` 下） |
| Bean 注入失败 | 检查类是否加了 `@Service` 等注解、是否在扫描范围内 |
| 编译不过但代码看着没问题 | `./mvnw clean compile` 清除过期 target/ |

## 前端

| 症状 | 排查方向 |
|------|----------|
| 跨域错误 | 在 `vite.config.ts` 配置 `server.proxy`，不要直接请求绝对地址 |
| tsc 报错但运行正常 | 类型问题仍需修复，`npm run build` 会拦截 |
| 依赖异常 | 删除 `node_modules` 与 lock 中损坏条目后重装 |
| 样式不生效 | 检查 import 顺序与 CSS 优先级，确认文件被引入 |

## 环境

- Java 版本：需 17+（`java -version` 确认）
- Node 版本：建议 18+（`node -v` 确认）
- macOS 上 `./mvnw` 无执行权限时：`chmod +x mvnw`

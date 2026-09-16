# 命令手册（按需加载）

> 仅在需要执行构建/运行/排查命令时读取本文件。

## 后端

```bash
cd backend
./mvnw spring-boot:run              # 启动，端口 8080
./mvnw clean package                # 打包（含测试）
./mvnw clean package -DskipTests    # 打包（跳过测试）
./mvnw clean compile                # 仅编译
./mvnw test                         # 运行测试
./mvnw test -Dtest=XxxTest          # 运行单个测试类
./mvnw test -Dtest=XxxTest#method   # 运行单个测试方法
./mvnw dependency:tree              # 查看依赖树
```

## 前端

```bash
cd frontend
npm install                         # 安装依赖
npm run dev                         # 开发服务器（Vite）
npm run build                       # tsc -b && vite build
npm run lint                        # Oxlint
npm run preview                     # 预览构建产物
```

## 联调

```bash
# 两个终端分别执行
cd backend && ./mvnw spring-boot:run
cd frontend && npm run dev
```

## 其他

```bash
git status / git diff / git log --oneline -5
lsof -i :8080                       # 检查端口占用（macOS）
```

# 前端规范（按需加载）

> 仅在涉及前端详细设计时读取本文件。

## 目录结构

```
frontend/src/
├── main.tsx        # 入口
├── App.tsx         # 根组件
├── App.css         # 根组件样式
├── index.css       # 全局样式
├── components/     # 通用组件（按需创建）
├── pages/          # 页面组件（按需创建）
├── hooks/          # 自定义 hooks（按需创建）
├── services/       # 接口调用封装（按需创建）
└── assets/         # 静态资源
```

## 编码约定

- 函数式组件 + TypeScript，导出用 `export function Xxx()`。
- 组件文件名 PascalCase；hooks 文件名 camelCase 且以 `use` 开头。
- 样式：跟随现有 CSS 方案；组件级样式与组件同目录或对应 `.css` 文件。
- 状态：局部用 `useState`/`useReducer`；不引入全局状态库。

## 接口调用

- 开发环境通过 Vite 代理访问后端（在 `vite.config.ts` 中配置 `server.proxy`）。
- fetch 封装放 `src/services/`，统一处理错误与基础 URL。

## 质量门槛

- `npm run lint`（Oxlint）无 error
- `npm run build`（tsc + vite build）通过

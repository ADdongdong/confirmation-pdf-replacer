# 函证头部替换工具 - React 前端

基于 **React 18 + Ant Design 5 + Webpack 5** 重写的组件化前端，业务逻辑与交互复刻旧版 Flask 模板（`upload.html`），UI 层调整为 antd 组件风格。

## 页面

| 路由 | 组件 | 功能 |
|------|------|------|
| `/` 及其他所有路径 | `BatchPage.jsx` | PDF 格式转函（唯一页面，自动加载默认配置，可处理单文件与批量） |

> 已去掉「单文件处理」「配置管理」两个独立页面——批量页内置配置面板（保存/加载配置 + 覆盖区域校准），功能已整合。

## 目录结构

```
frontend/
├── public/index.html          # HTML 模板
├── src/
│   ├── index.js               # 入口
│   ├── App.jsx                # 路由（所有路径 → BatchPage）
│   ├── pages/BatchPage.jsx    # PDF 格式转函页面（唯一页面）
│   ├── components/PreviewOverlay.jsx  # 预览遮罩拖拽组件（上界覆盖 + 下界页脚）
│   ├── api/                   # axios 封装 + API 契约
│   └── styles/global.css      # 全局样式
├── webpack.config.js          # 构建配置
├── babel.config.js
└── package.json
```

## 构建

```bash
npm install
npm run build     # 产物输出到 ../python/static/react/
npm run dev       # 开发模式（webpack-dev-server :3000，代理 API 到 :8888）
```

构建产物输出到 `python/static/react/`，同时被两个后端服务：
- **Python 版**（端口 8888）：Flask 通过 `render_react()` 返回
- **Java 版**（端口 8889）：`WebServer.java` 从 `../python/static/react/` 读取（API 契约已对齐 Python）

> 构建产物 `publicPath` 为 `/static/react/`，Java 端 `StaticHandler` 按此路径服务静态资源。

## 说明

- **路由方案**：使用 `HashRouter`，所有路径（`/`、`/upload`、`/config`、`/batch`）统一重定向到 `/upload`，仅渲染 `BatchPage`。
- **后端 API 不变**：所有请求仍走 Flask 8888 端口的原接口（`/preview`、`/generate`、`/batch/*`、`/config/*`）。
- **预览遮罩**：上界覆盖区（蓝）+ 下界页脚区（蓝，页码行遮盖）+ 绿色表格保护线，均可拖拽调整。
- **业务逻辑保持一致**：覆盖下界拖拽范围、forceWhiteout 优先级、300 字校验、20 文件上限、收函单位置信度两档等均与旧版一致。

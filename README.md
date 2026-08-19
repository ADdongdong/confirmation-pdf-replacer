# 函证 PDF 头部内容替换工具

券商项目组函证复核提效工具 — 在会计事务所已有函证 PDF 的基础上，自动替换函证说明、地址、联系人等信息为券商项目组信息，实现快速发函。

## 项目结构

```
hanzheng_pdf_tool_project/
├── README.md                 # 本说明文件
│
├── memory/                   # 开发记录（CodeBuddy 会话日志）
│   ├── MEMORY.md             # 长期记忆：项目总览 + 全局偏好
│   ├── 2026-06-26.md         # v1~v4 核心引擎开发 + Web 表单
│   ├── 2026-06-29.md         # 加粗提示行 + 两栏布局
│   ├── 2026-06-30.md         # Java 版完整迁移（Python → Java）
│   ├── 2026-07-01.md         # 功能介绍 PPT + 配色方案
│   ├── 2026-07-14.md         # Bug 修复（坐标系/重影/乱码）
│   └── 2026-07-16.md         # Python 版同步框选 + 批量处理设计
│
├── java/                     # Java 版（★ 主用方案，端口 8889）
│   ├── pom.xml               # Maven 配置（JDK 1.8 + PDFBox 2.0.27）
│   ├── run.bat               # 一键启动（-Xmx1024m）
│   ├── start_server.bat      # Web 服务启动
│   ├── README.md             # Java 版详细说明
│   ├── lib/                  # 依赖 JAR（pdfbox, fontbox, commons-logging）
│   ├── fonts/NSimSun.ttf     # 宋体字体
│   └── src/main/java/com/hanzheng/
│       ├── HanzhengPdfTool.java      # CLI 命令行入口
│       ├── WebServer.java            # HTTP Web 服务（JDK 内置 HttpServer）
│       ├── core/
│       │   ├── PdfProcessor.java     # PDF 处理核心
│       │   ├── FormatExtractor.java  # 格式自动提取
│       │   └── CjkWrapper.java       # CJK 手动断行
│       ├── model/
│       │   ├── HanzhengRequest.java  # 请求模型
│       │   └── PdfFormat.java        # 格式模型
│       └── resources/
│           └── index.html            # 内嵌 Web 前端
│
├── frontend/                 # React + Ant Design 前端（★ 当前 UI 方案）
│   ├── src/
│   │   ├── App.jsx           # 路由（HashRouter）+ 顶部导航
│   │   ├── pages/            # 三个页面：SinglePage / BatchPage / ConfigPage
│   │   ├── components/       # PreviewOverlay 预览遮罩拖拽组件
│   │   └── api/              # axios 封装 + API 契约
│   ├── webpack.config.js     # 构建配置（产物输出到 python/static/react/）
│   └── package.json
│
└── python/                   # Python 版（备选方案，端口 8888）
    ├── web_form_server.py    # Flask Web 服务（页面路由渲染 React 产物）
    ├── replace_header_v4.py  # 核心处理引擎（PyMuPDF + pdfplumber）
    ├── templates/index.html  # Web 前端模板（旧版，React 未构建时回退）
    ├── static/react/         # React 构建产物（webpack 输出）
    ├── static/index.html     # 独立批量替换工具（勿动）
    ├── configs/              # 话术配置文件
    │   ├── broker_header.json
    │   ├── broker_home.txt
    │   └── broker_home_v4.txt
    └── fonts/NSimSun.ttf     # 宋体字体
```

## 业务背景

券商函证本质上是对会计事务所已完成的财务函证进行复核。会所已经做过一次函证，券商只需对相同的财务数据再做一次复核。

实践中，项目组常直接修改会所的 PDF —— 将前面的函证说明、地址、联系人等信息替换为券商项目组的信息后直接发函，这是最快、错误最少的操作方式。

## 核心功能

| 功能 | 说明 |
|------|------|
| **智能格式提取** | 自动识别原 PDF 字号、边距、行距，保持原有排版风格 |
| **分区域处理** | 头部白化重写（CJK 手动断行）+ 表格区域完全保护 |
| **联系方式布局** | 两栏自适应排版 · 伪加粗提示行 · 行距自适应 |
| **双版本架构** | Java (PDFBox) 主用 + Python (PyMuPDF) 备选 |
| **双入口** | Web 表单 + CLI 命令行 |

## 快速启动

### Java 版（推荐）
```batch
# 启动 Web 服务（端口 8889）
双击 run.bat
# 浏览器访问 http://localhost:8889
```

### Python 版（备选）
```bash
# 需要 conda 环境 pytorch
conda activate pytorch
pip install flask PyMuPDF pdfplumber
python web_form_server.py
# 浏览器访问 http://localhost:8888
```

## 环境依赖

- **Java**: JDK 1.8 + PDFBox 2.0.27
- **Python**: conda pytorch 环境 + Flask + PyMuPDF (fitz) + pdfplumber
- **字体**: NSimSun.ttf（宋体）

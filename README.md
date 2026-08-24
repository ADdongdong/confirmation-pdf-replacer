# 函证 PDF 头部替换工具

券商项目组函证复核提效工具 — 在会计事务所已有函证 PDF 的基础上，自动把函证说明、地址、联系人、回函地址等信息替换为券商项目组信息，实现快速发函。

## 业务背景

券商函证本质是对会计事务所已完成的财务函证进行复核。会所已做过一次函证，券商只需对相同财务数据再复核一次。

实践中，项目组常直接修改会所的 PDF —— 将原 PDF 头部的函证说明、地址、联系人替换为券商项目组信息后直接发函，这是最快、错误最少的操作方式。本工具把这一手动过程自动化。

## 架构总览

```
前端（React 18 + Ant Design）  ──HTTP API──►  后端（Java / Python 二选一）
        python/static/react/                      端口 8889（Java）  /  8888（Python）
```

- **Java 版**（端口 8889，PDFBox 实现）：零额外 Web 依赖（JDK 内置 HttpServer），编译用 `javac`，无需 Maven。
- **Python 版**（端口 8888，PyMuPDF 实现）：Flask Web 服务。
- 两版**共用同一套 React 前端产物**（`python/static/react/`），后端 API 契约完全对齐，功能与界面一致。

## 目录结构

```
hanzheng_pdf_tool_project/
├── README.md                  # 本说明文件
├── python_server.bat          # Python 版 Web 服务启动脚本（端口 8888）
├── java_server.bat            # Java 版 Web 服务启动脚本（端口 8889）
│
├── frontend/                  # React + Ant Design 前端工程
│   ├── src/
│   │   ├── App.jsx            # HashRouter 路由（所有路径重定向到 /upload）
│   │   ├── pages/
│   │   │   └── BatchPage.jsx  # 唯一页面：PDF 格式转函（含上传/批量/配置面板）
│   │   ├── components/
│   │   │   └── PreviewOverlay.jsx  # 预览遮罩：上界覆盖 + 下界页脚遮盖，可拖拽
│   │   ├── api/               # axios 封装 + API 契约
│   │   └── styles/            # 样式
│   ├── webpack.config.js      # 构建配置（产物输出到 python/static/react/）
│   └── package.json
│
├── python/                    # Python 版（端口 8888）
│   ├── web_form_server.py     # Flask Web 服务（渲染 React 产物）
│   ├── replace_header_v4.py   # 核心处理引擎（PyMuPDF + pdfplumber）
│   ├── configs/               # 配置（JSON）
│   │   ├── _default.txt       # 默认配置指针（仅存默认配置名）
│   │   ├── broker_header.json # 内置话术/头部模板
│   │   └── *.json             # 用户保存的配置
│   ├── templates/             # 页面模板
│   ├── static/react/          # React 构建产物（webpack 输出）
│   ├── fonts/                 # NSimSun.ttf 等中文字体
│   └── outputs/               # 生成结果输出目录
│
├── java/                      # Java 版（端口 8889）
│   ├── build.bat              # javac 编译（输出到 target/classes）
│   ├── start.bat              # Web 服务启动（先停旧进程再启动）
│   ├── lib/                   # PDFBox 依赖 jar
│   ├── fonts/NSimSun.ttf      # 中文字体（宋体）
│   ├── configs/               # 配置（JSON，与 Python 版格式一致）
│   └── src/main/java/com/hanzheng/
│       ├── HanzhengPdfTool.java   # CLI 命令行入口
│       ├── WebServer.java         # Web 服务（API 契约对齐 Python）
│       ├── core/
│       │   ├── PdfProcessor.java      # PDF 白化 + 头部重写 + 页脚遮盖
│       │   ├── FormatExtractor.java   # 格式自动提取（字号/边距/行距）
│       │   ├── CjkWrapper.java        # CJK 文本手动断行
│       │   ├── PdfPreviewUtil.java    # PDF → PNG 预览图渲染
│       │   ├── ConfigManager.java     # 配置管理（Python 契约 JSON）
│       │   └── RecipientExtractor.java# 收函单位自动提取
│       └── model/
│           ├── PdfFormat.java         # 格式参数
│           ├── HanzhengRequest.java    # 请求参数（含回函地址、页脚高度）
│           └── BatchJob.java          # 批量作业
│
├── data/                      # 示例/测试 PDF
├── docs/                      # 需求规格说明书（v5.0 docx + md）
└── memory/                    # 开发记录（CodeBuddy 会话日志）
```

## 核心功能

| 功能 | 说明 |
|------|------|
| **智能格式提取** | 自动识别原 PDF 字号、边距、行距，保持原有排版风格 |
| **分区域处理** | 头部白化重写（CJK 手动断行）+ 表格区域完全保护 |
| **联系方式布局** | 两栏自适应排版 · 伪加粗提示行 · 行距自适应 |
| **覆盖区域校准** | 上界覆盖（蓝遮罩，可拖拽微调）+ 下界页脚遮盖（蓝遮罩）；上界首次用自动检测结果作初始值，之后完全按用户修改保存/加载（所见即所得） |
| **配置管理** | 保存/加载/删除配置模板；可设为默认配置，进入页面自动加载 |
| **批量处理** | 一次上传多文件（≤20），ZIP 打包下载 |
| **双版本架构** | Java (PDFBox) + Python (PyMuPDF)，共用同一前端、API 契约一致 |
| **双入口** | Web 表单 + Java CLI 命令行 |

## 快速启动

### 方式一：一键启动脚本（推荐）

直接双击根目录脚本：

```batch
python_server.bat     # 启动 Python 版（端口 8888）
java_server.bat       # 启动 Java 版（端口 8889）
```

启动后浏览器访问对应地址：
- Python 版：`http://localhost:8888`
- Java 版：`http://localhost:8889`

### 方式二：命令行启动

**Java 版**（先编译再启动）：
```batch
cd java
build.bat          # javac 编译到 target/classes
start.bat          # 启动（端口 8889）

:: CLI 命令行模式
java -cp "target\classes;lib\pdfbox-2.0.27.jar;lib\fontbox-2.0.27.jar;lib\commons-logging-1.2.jar" com.hanzheng.HanzhengPdfTool 输入.pdf -t 话术.txt -o 输出.pdf
```

**Python 版**（需 conda 环境 pytorch）：
```bash
conda activate pytorch
pip install flask PyMuPDF pdfplumber
cd python
python web_form_server.py     # 端口 8888
```

### 前端构建

修改 `frontend/` 后需重新构建，产物输出到 `python/static/react/`，Java / Python 两版共用：
```batch
cd frontend && npm install && npm run build
```

## 使用说明（Web 页面）

1. 进入 `/upload`（Python 版即 `http://localhost:8888/upload`），自动加载默认配置。
2. 填写联系方式：项目联系人/电话、收件人/电话、邮箱、**回函地址**。
3. 上传会所函证 PDF，预览区显示替换效果；可拖拽遮罩微调上界覆盖与下界页脚遮盖。
4. 可「保存为配置」，勾选「保存为默认配置」后下次进入自动加载。
5. 单文件点生成下载；多文件批量上传后打包 ZIP 下载。

## 环境依赖

- **Java**：JDK 1.8 + PDFBox 2.0.27（`java/lib/` 已内置）
- **Python**：conda `pytorch` 环境 + Flask + PyMuPDF (fitz) + pdfplumber
- **前端**：Node.js + React 18 + Ant Design 5 + webpack 5
- **字体**：`NSimSun.ttf`（宋体），缺失时自动回退到 `C:\Windows\Fonts\simsun.ttc`

## API 契约（Java / Python 一致）

- 页面路由：`/` `/upload` `/config` `/batch` → React 前端（HashRouter）；`/batch` 重定向到 `/upload`
- 静态资源：`/static/react/*` → React 构建产物
- 单文件：`POST /generate`、`POST /preview`、`GET /preview-img/<filename>`
- 批量：`POST /batch/upload`、`GET /batch/preview/<token>/<fn>`、`POST /batch/re-recognize`、`POST /batch/generate`、`GET /batch/download/<token>`
- 配置：`GET /config/list`、`GET /config/load/<filename>`、`POST /config/save`、`POST /config/delete/<filename>`、`GET /config/default`、`POST /config/preview`

## 文档

- 需求规格说明书：`docs/函证PDF头部替换工具_需求规格说明书_v5.0.md`（含 docx 版）
- Java 版说明：`java/README.md`

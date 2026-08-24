# 函证头部替换工具（Java 版）

自动替换会所函证 PDF 头部为券商格式。**功能、界面与 Python 版完全同步**（共用同一套 React 前端，后端 API 契约对齐 Python）。

## 技术栈

| 组件 | 版本 |
|------|------|
| JDK | 1.8 |
| Apache PDFBox | 2.0.27 |
| 构建 | javac（无需 Maven） |
| Web 服务器 | JDK 内置 HttpServer（零额外依赖） |
| 前端 | React 18 + Ant Design（复用 `../python/static/react/` 构建产物） |

## 项目结构

```
java/
├── build.bat                    # javac 编译（输出到 target/classes）
├── start.bat                    # 前台启动
├── start_background.bat         # 后台启动（日志到 server.log）
├── fonts/NSimSun.ttf            # 中文字体（宋体）
├── lib/                         # PDFBox 依赖 jar
├── configs/                     # 配置（JSON，与 Python 版格式一致）
└── src/main/java/com/hanzheng/
    ├── HanzhengPdfTool.java     # CLI 主入口
    ├── WebServer.java           # Web 服务（API 契约对齐 Python）
    ├── core/
    │   ├── CjkWrapper.java      # CJK 文本手动断行
    │   ├── FormatExtractor.java # PDF 格式自动提取（含 tableY）
    │   ├── PdfProcessor.java    # PDF 白化 + 头部重写 + 页脚遮盖
    │   ├── PdfPreviewUtil.java  # PDF → PNG 预览图渲染
    │   ├── ConfigManager.java   # 配置管理（Python 契约 JSON）
    │   └── RecipientExtractor.java # 收函单位自动提取
    └── model/
        ├── PdfFormat.java       # 格式参数
        ├── HanzhengRequest.java # 请求参数（含回函地址、页脚高度）
        └── BatchJob.java        # 批量作业（对齐 Python session）
```

## 编译运行

```bash
# 1. 编译（输出到 target/classes）
build.bat

# 2. 启动（前台）
start.bat
# 或后台：start_background.bat
```

然后访问 http://localhost:8889

> 前端依赖 `../python/static/react/` 构建产物，若该目录不存在会回退到 classpath 的 index.html。
> 重新构建 React 前端后（`cd ../frontend && npm run build`），Java 版自动使用新产物。

## 与 Python 版功能同步清单

| 功能 | Python 版 | Java 版 |
|------|-----------|---------|
| React 前端 | ✅ | ✅ 复用同一产物 |
| 批量处理（≤20，ZIP 下载） | ✅ | ✅ |
| 收函单位自动提取（high/low） | ✅ | ✅ |
| 覆盖区域校准（上界蓝遮罩） | ✅ | ✅ |
| 页脚遮盖（下界蓝遮罩） | ✅ | ✅ |
| 回函地址 | ✅ | ✅ |
| 配置管理（中文 contacts） | ✅ | ✅ |
| 预览图 PNG | ✅ | ✅ PDFBox PDFRenderer |
| 置信度两档 | ✅ | ✅ |

## API 契约（与 Python web_form_server.py 一致）

- `GET /` `/upload` `/config` `/batch` → React 前端（HashRouter）
- `GET /static/react/*` → React 静态资源
- `POST /generate` → 单文件（multipart，contacts 中文 + 回函地址 + footer_height）
- `POST /preview`、`GET /preview-img/<filename>` → 预览 PNG
- `POST /batch/upload`、`GET /batch/preview/<token>/<fn>`、`POST /batch/re-recognize`、`POST /batch/generate`、`GET /batch/download/<token>`
- `GET /config/list`、`GET /config/load/<filename>`、`POST /config/save`、`POST/DELETE /config/delete/<filename>`、`GET /config/default`、`POST /config/preview`

## CLI 命令行模式

```bash
java -cp "target\classes;lib\pdfbox-2.0.27.jar;lib\fontbox-2.0.27.jar;lib\commons-logging-1.2.jar" com.hanzheng.HanzhengPdfTool 输入.pdf -t 话术.txt -o 输出.pdf
```

## 注意事项

- 字体 `NSimSun.ttf` 已放入 `fonts/`，缺失时自动查找 `C:\Windows\Fonts\simsun.ttc`
- 中文路径下编译/启动用 `build.bat`/`start.bat`（内部 chcp 65001 + `%~dp0`）

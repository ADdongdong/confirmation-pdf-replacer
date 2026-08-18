# 函证头部替换工具（Java 版）

自动替换会所函证 PDF 头部为券商格式。完全替代 Python 版本的功能。

## 技术栈

| 组件 | 版本 |
|------|------|
| JDK | 1.8 |
| Apache PDFBox | 2.0.27 |
| 构建工具 | Maven 3.x |
| Web 服务器 | JDK 内置 HttpServer（零额外依赖） |

## 项目结构

```
hanzheng_pdf_tool_java/
├── pom.xml                          # Maven 配置
├── fonts/
│   └── NSimSun.ttf                  # 中文字体（宋体）
├── src/main/java/com/hanzheng/
│   ├── HanzhengPdfTool.java         # CLI 主入口
│   ├── WebServer.java               # Web 表单服务
│   ├── core/
│   │   ├── CjkWrapper.java          # CJK 文本手动断行
│   │   ├── FormatExtractor.java     # PDF 格式自动提取
│   │   └── PdfProcessor.java        # PDF 白化 + 文本替换
│   └── model/
│       ├── PdfFormat.java           # 格式参数
│       └── HanzhengRequest.java     # 请求参数
└── src/main/resources/
    └── index.html                   # Web 表单页面
```

## 编译运行

### 1. 安装依赖

确保已安装：
- **JDK 1.8**（`java -version` 确认）
- **Maven 3.x**（`mvn -version` 确认）

### 2. 编译打包

```bash
cd hanzheng_pdf_tool_java
mvn clean package -DskipTests
```

编译成功后在 `target/` 下生成：
- `hanzheng-pdf-tool-1.0.0.jar` — 普通 JAR
- `hanzheng-pdf-tool-1.0.0-jar-with-dependencies.jar` — 带依赖的 fat JAR（推荐）

### 3. CLI 命令行模式

```bash
java -jar target/hanzheng-pdf-tool-1.0.0-jar-with-dependencies.jar \
     会所函证.pdf -t 话术.txt -o 输出.pdf
```

话术文件格式（与 Python 版完全一致）：

```
TITLE:企业询证函
致：中信证券股份有限公司
　　本公司聘请的XX证券正在对本公司进行尽职调查...
---
项目联系人：张三
项目联系人电话：13800138000
收件人：开源证券投行部
收件人电话：0755-88888888
邮箱：touhang@kzq.com.cn
```

### 4. Web 表单模式

```bash
java -cp target/hanzheng-pdf-tool-1.0.0-jar-with-dependencies.jar com.hanzheng.WebServer
```

然后浏览器访问 http://localhost:8888

## 功能对比（与 Python 版）

| 功能 | Python 版 (pymupdf) | Java 版 (PDFBox) |
|------|---------------------|-------------------|
| 格式自动提取 | ✅ pdfplumber | ✅ PDFTextStripper 扩展 |
| 头部白化 | ✅ Redaction | ✅ 白色矩形覆盖 |
| CJK 手动断行 | ✅ | ✅ 完全一致算法 |
| 标题居中 | ✅ | ✅ |
| 联系方式两栏 | ✅ | ✅ |
| 伪加粗 | ✅ 双重渲染 | ✅ 双重渲染 |
| 表格保护 | ✅ | ✅ |
| Web 表单 | ✅ Flask | ✅ JDK HttpServer |
| 中文宋体嵌入 | ✅ | ✅ PDType0Font |
| CLI 话术文件 | ✅ | ✅ 完全兼容 |

## 与 Python 版的主要差异

1. **白化方式不同**：Python 版用 pymupdf 的 Redaction 机制（真实删除），Java 版用 PDFBox 的白色矩形覆盖（视觉白化）。原文字仍在 PDF 中但被白色遮住。
2. **字号计算略有差异**：PDFBox 提取的字号精度可能略有不同，但正文字号误差在 ±0.5pt 内。
3. **坐标系统**：PDFBox 使用 PDF 标准坐标系（左下角原点），代码中已自动转换。

## 注意事项

- 字体文件 `NSimSun.ttf` 已放入 `fonts/` 目录
- 如果字体缺失，程序会自动查找 `C:\Windows\Fonts\simsun.ttc`
- Web 模式生成的文件保存在 `outputs/` 目录

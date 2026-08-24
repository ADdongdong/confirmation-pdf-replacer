# 投行产品设计师 - 长期记忆

## 项目：函证PDF头部替换工具

### 使用场景
券商收到会所的函证PDF，需要用券商标准话术重写头部内容，保留下方科目表格数据不变。

### 当前主用方案（Java版）
- **Web 服务**: `hanzheng_pdf_tool_java/` - JDK 1.8 + PDFBox 2.0.27
- **端口**: 8889
- **启动方式**: 双击 `run.bat` 或命令行运行（-Xmx1024m 堆内存）
- **编译**: JDK 1.8 `C:\Program Files\Java\jdk1.8.0_301`，classpath 含 `lib/pdfbox-2.0.27.jar`
- **前端**: `src/main/resources/index.html`（内嵌 HTML，无需 Flask/Python）
- **代码结构**:
  - `WebServer.java` - HTTP 服务（/generate, /preview, /preview-img/, /download/）
  - `PdfProcessor.java` - PDF 处理核心（白化+重写+加粗）
  - `FormatExtractor.java` - 自动提取原PDF格式参数
  - `HanzhengRequest.java` - 请求模型
  - `CjkWrapper.java` - CJK 断行

### 核心功能
1. **自动格式提取**: 从原PDF读取正文字号、标题字号、左边距、段落宽度、行间距、表格起始y坐标
2. **白色遮盖**: 用白色矩形覆盖头部区域（用户可自定义底部边界 Y 坐标）
3. **内容重写**: 标题居中加粗、正文左对齐、提示语加粗、联系方式两栏布局
4. **可视化框选**: 上传PDF后可预览首页，拖动红色遮罩底部手柄调整覆盖范围（`/preview` API 渲染首页为PNG）

### 设计原则（来自用户反馈）
- 保留会所原格式（字体、字号、段落宽度），仅替换文字内容
- "加粗"用伪加粗（三重绘制偏移 ±0.4/0.8pt），因为用的宋体无原生Bold变体

### 备选方案（Python版，不再维护）
- `web_form_server.py` + `replace_header_v4.py`（Flask，端口 8888）
- 已停止使用，改用 Java 版

### 全局偏好（适用于所有项目）

- **流程图/框架图绘制**：尽量使用 **Mermaid** 语法（mermaid 代码块）。Mermaid 在 CodeBuddy 中渲染良好，便于维护和修改。

### Mermaid 语法避坑要点（实测踩坑）
- **节点标签里不能出现英文冒号 `:`**（即便用引号包裹的菱形节点 `{}` 也会报错 `Expecting ... got 'STR'`）
  - 修正：用全角 `：`、空格、或者干脆改写措辞（如 `是否找到` 代替 `是否找到:`）
- 节点标签含特殊字符（`/`、`：`、`&`）时，**用双引号包裹**最稳妥：`API1["/template<br/>GET/POST"]`
- 决策菱形 `{}` 内放纯中文短语最稳；如必须含标点，优先用全角符号
- `<br/>` 换行在 CodeBuddy 渲染中工作良好

## 用户偏好（个人）
- Python 环境: conda pytorch 子环境 `C:\ProgramData\Anaconda3\envs\pytorch`
- JDK: `C:\Program Files\Java\jdk1.8.0_301`

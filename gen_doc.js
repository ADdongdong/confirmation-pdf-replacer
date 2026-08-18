/**
 * 函证 PDF 头部替换工具 — 需求规格说明书 (Word)
 */
const fs = require('fs');
const path = require('path');

const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  ImageRun, Header, Footer, AlignmentType, HeadingLevel,
  BorderStyle, WidthType, ShadingType, PageNumber, PageBreak,
  LevelFormat, TableOfContents
} = require('docx');

const OUT_DIR = __dirname;
const DIAG_DIR = path.join(__dirname, 'python', 'visio_png');

const A4_W = 11906; // DXA
const A4_H = 16838;
const CONTENT_W = 9026; // A4 - 1" margins each side
const MARGIN = 1440;

// ---- helpers ----
function p(text, opts = {}) {
  const { bold, size, color, alignment, heading, spacing, indent } = opts;
  return new Paragraph({
    ...(heading ? { heading } : {}),
    ...(alignment ? { alignment } : {}),
    ...(spacing ? { spacing } : {}),
    ...(indent ? { indent } : {}),
    children: [new TextRun({ text, bold, size: size || 24, font: 'Arial', color: color || '333333' })],
  });
}

function multiP(runs) {
  return new Paragraph({
    spacing: { after: 60 },
    children: runs.map(r => new TextRun({
      text: r.t, bold: r.b || false, size: r.s || 24, font: 'Arial', color: r.c || '333333'
    })),
  });
}

function headerP(text) {
  return new Paragraph({
    children: [new TextRun({ text, size: 18, font: 'Arial', color: '888888' })],
  });
}

const cellBorder = { style: BorderStyle.SINGLE, size: 1, color: 'CCCCCC' };
const cellBorders = { top: cellBorder, bottom: cellBorder, left: cellBorder, right: cellBorder };
const headerBorder = { style: BorderStyle.SINGLE, size: 2, color: '2E75B6' };
const headerBorders = { top: headerBorder, bottom: headerBorder, left: headerBorder, right: cellBorder };

function makeCell(text, opts = {}) {
  const { bold, shading, width, align } = opts;
  return new TableCell({
    borders: cellBorders,
    ...(width ? { width: { size: width, type: WidthType.DXA } } : {}),
    ...(shading ? { shading: { fill: shading, type: ShadingType.CLEAR } } : {}),
    margins: { top: 60, bottom: 60, left: 100, right: 100 },
    children: [new Paragraph({
      alignment: align || AlignmentType.LEFT,
      children: [new TextRun({ text, bold: bold || false, size: 20, font: 'Arial' })],
    })],
  });
}

function makeHeaderCell(text, width) {
  return new TableCell({
    borders: headerBorders,
    width: { size: width, type: WidthType.DXA },
    shading: { fill: '2E75B6', type: ShadingType.CLEAR },
    margins: { top: 60, bottom: 60, left: 100, right: 100 },
    children: [new Paragraph({
      children: [new TextRun({ text, bold: true, size: 20, font: 'Arial', color: 'FFFFFF' })],
    })],
  });
}

function makeTable(headers, rows, colWidths) {
  const totalW = colWidths.reduce((a, b) => a + b, 0);
  return new Table({
    width: { size: totalW, type: WidthType.DXA },
    columnWidths: colWidths,
    rows: [
      new TableRow({ children: headers.map((h, i) => makeHeaderCell(h, colWidths[i])) }),
      ...rows.map(row =>
        new TableRow({ children: row.map((cell, i) => makeCell(cell, { width: colWidths[i] })) })
      ),
    ],
  });
}

function imgPara(filename, w, h) {
  const fp = path.join(DIAG_DIR, filename);
  if (!fs.existsSync(fp)) return p(`[图: ${filename} (未找到)]`, { size: 18, color: 'CC0000' });
  const buf = fs.readFileSync(fp);
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 200, after: 80 },
    children: [new ImageRun({
      type: 'png',
      data: buf,
      transformation: { width: w, height: h },
      altText: { title: filename, description: filename, name: filename },
    })],
  });
}

function figCaption(num, text) {
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { after: 200 },
    children: [new TextRun({ text: `图${num} ${text}`, size: 18, font: 'Arial', color: '888888', italics: true })],
  });
}

function bulletItem(text, ref = 'bullets') {
  return new Paragraph({
    numbering: { reference: ref, level: 0 },
    spacing: { after: 40 },
    children: [new TextRun({ text, size: 22, font: 'Arial', color: '333333' })],
  });
}

function numItem(text, ref = 'numbers') {
  return new Paragraph({
    numbering: { reference: ref, level: 0 },
    spacing: { after: 40 },
    children: [new TextRun({ text, size: 22, font: 'Arial', color: '333333' })],
  });
}

// =============================================
// Build Document
// =============================================

const doc = new Document({
  styles: {
    default: { document: { run: { font: 'Arial', size: 24 } } },
    paragraphStyles: [
      { id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 40, bold: true, font: 'Arial', color: '1A3A5C' },
        paragraph: { spacing: { before: 480, after: 240 }, outlineLevel: 0 } },
      { id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 32, bold: true, font: 'Arial', color: '2E75B6' },
        paragraph: { spacing: { before: 360, after: 180 }, outlineLevel: 1 } },
      { id: 'Heading3', name: 'Heading 3', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 26, bold: true, font: 'Arial', color: '2E75B6' },
        paragraph: { spacing: { before: 240, after: 120 }, outlineLevel: 2 } },
    ],
  },
  numbering: {
    config: [
      { reference: 'bullets',
        levels: [{ level: 0, format: LevelFormat.BULLET, text: '\u2022', alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 720, hanging: 360 } } } }] },
      { reference: 'bullets2',
        levels: [{ level: 0, format: LevelFormat.BULLET, text: '\u25CB', alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 1080, hanging: 360 } } } }] },
      { reference: 'numbers',
        levels: [{ level: 0, format: LevelFormat.DECIMAL, text: '%1.', alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 720, hanging: 360 } } } }] },
    ],
  },
  sections: [
    // ============ 封面 ============
    {
      properties: {
        page: { size: { width: A4_W, height: A4_H }, margin: { top: MARGIN, right: MARGIN, bottom: MARGIN, left: MARGIN } },
      },
      children: [
        new Paragraph({ spacing: { before: 3600 } }),
        p('函证 PDF 头部替换工具', { bold: true, size: 56, color: '1A3A5C', alignment: AlignmentType.CENTER }),
        new Paragraph({ spacing: { after: 400 } }),
        p('需求规格说明书', { bold: true, size: 40, color: '2E75B6', alignment: AlignmentType.CENTER }),
        new Paragraph({ spacing: { after: 600 } }),
        p('版本 v3.0', { size: 24, color: '888888', alignment: AlignmentType.CENTER }),
        p('2026年7月16日', { size: 24, color: '888888', alignment: AlignmentType.CENTER }),
        new Paragraph({ spacing: { before: 1200 } }),
        p('券商投行函证复核提效工具', { size: 22, color: 'AAAAAA', alignment: AlignmentType.CENTER }),
        new Paragraph({ children: [new PageBreak()] }),
      ],
    },

    // ============ 目录页 ============
    {
      properties: {
        page: { size: { width: A4_W, height: A4_H }, margin: { top: MARGIN, right: MARGIN, bottom: MARGIN, left: MARGIN } },
      },
      headers: {
        default: new Header({ children: [headerP('函证 PDF 头部替换工具 — 需求规格说明书')] }),
      },
      footers: {
        default: new Footer({ children: [new Paragraph({
          alignment: AlignmentType.CENTER,
          children: [new TextRun({ text: '— ', size: 18, color: '888888' }), new TextRun({ children: [PageNumber.CURRENT], size: 18, color: '888888' }), new TextRun({ text: ' —', size: 18, color: '888888' })],
        })] }),
      },
      children: [
        new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun('目录')] }),
        new TableOfContents('目录', { hyperlink: true, headingStyleRange: '1-3' }),
        new Paragraph({ children: [new PageBreak()] }),
      ],
    },

    // ============ 正文 ============
    {
      properties: {
        page: { size: { width: A4_W, height: A4_H }, margin: { top: MARGIN, right: MARGIN, bottom: MARGIN, left: MARGIN } },
      },
      headers: {
        default: new Header({ children: [headerP('函证 PDF 头部替换工具 — 需求规格说明书')] }),
      },
      footers: {
        default: new Footer({ children: [new Paragraph({
          alignment: AlignmentType.CENTER,
          children: [new TextRun({ text: '— ', size: 18, color: '888888' }), new TextRun({ children: [PageNumber.CURRENT], size: 18, color: '888888' }), new TextRun({ text: ' —', size: 18, color: '888888' })],
        })] }),
      },
      children: [

        // ====== 第1章 ======
        new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun('第1章 项目概述')] }),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('1.1 项目背景')] }),
        p('券商（投行）在 IPO、再融资等业务中，需要对被尽调企业的财务数据进行函证复核。实践中，会计师事务所已完成过一轮函证，券商只需对其函证结果进行二次复核。'),
        new Paragraph({ spacing: { after: 60 } }),
        p('项目组常见的操作方式是直接修改会所的函证 PDF —— 将函证说明、地址、联系人等信息替换为券商项目组的信息后重新发函。这种方式最快、错误率最低，但手工逐份修改效率低下、容易出错。'),
        new Paragraph({ spacing: { after: 60 } }),
        p('本工具旨在实现该流程的自动化：上传会所函证 PDF，自动识别并替换头部内容，保留下方科目表格数据不变，支持批量处理。'),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('1.2 项目目标')] }),
        bulletItem('实现会所函证 PDF 头部的自动化替换，包括：函证标题、正文话术、联系方式等'),
        bulletItem('智能提取原 PDF 的排版格式（字号、边距、行距），保持替换后的 PDF 与原文件风格一致'),
        bulletItem('自动识别原 PDF 中的收函单位名称，减少人工输入'),
        bulletItem('支持批量上传和处理多个 PDF，一键打包下载，大幅提升工作效率'),
        bulletItem('提供 Web 操作界面，零安装客户端，浏览器即可使用'),
        bulletItem('支持配置模板的创建、保存、加载，适配不同项目、不同会所的格式差异'),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('1.3 适用范围')] }),
        p('本工具适用于券商投行项目组对各类会计师事务所出具的往来款项询证函、交易询证函 PDF 进行头部内容替换。支持的会所包括但不限于：立信、天健、大华、信永中和、容诚等。'),
        new Paragraph({ spacing: { after: 60 } }),
        p('输入格式：PDF（单页或多页，仅首页头部被处理，其余页面不做任何修改）。'),
        new Paragraph({ spacing: { after: 60 } }),
        p('输出格式：PDF（替换后），批量输出为 ZIP 压缩包。'),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('1.4 术语定义')] }),
        makeTable(
          ['术语', '说明'],
          [
            ['会所函证 PDF', '会计师事务所出具的询证函 PDF 文件，是本工具的输入源'],
            ['券商模板', '券商项目组定义的标准话术，包括函证正文和联系方式'],
            ['收函单位', '被询证的企业名称，须从原 PDF 自动提取，可人工修正'],
            ['覆盖区域 / 白化', 'PDF 首页从顶部到表格区域之间的部分，需要被白色覆盖后重写'],
            ['表格保护线', 'PDF 首页表格起始位置的 Y 坐标，白化区域不得越过此线'],
            ['CJK 断行', '中日韩字符的手动换行算法，避免自动换行时标点错位'],
            ['覆盖下界 (whiteout_bottom)', '白化区域的底部 Y 坐标，支持自动检测或手动指定'],
          ],
          [2500, 6526]
        ),
        new Paragraph({ spacing: { after: 120 } }),

        new Paragraph({ children: [new PageBreak()] }),

        // ====== 第2章 ======
        new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun('第2章 业务需求')] }),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('2.1 业务场景')] }),
        p('某券商投行项目组正在进行 IPO 尽职调查，需要向被尽调企业的客户、供应商发函确认往来款项。会计师事务所需先行完成第一轮函证，券商收到会所完成的函证 PDF 后进行复核。项目组需要将 PDF 中的函证抬头、正文说明、回函联系方式替换为券商项目组的信息，然后将修改后的 PDF 直接发函。'),
        new Paragraph({ spacing: { after: 200 } }),

        imgPara('01_business_flow.png', 5700000, 805574),
        figCaption('1', '业务流程图'),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('2.2 用户角色')] }),
        makeTable(
          ['角色', '职责', '使用频率'],
          [
            ['投行项目组成员', '上传会所函证 PDF，核对收函单位，生成替换后的函证', '高（每日）'],
            ['项目负责人', '创建和维护券商函证模板（话术 + 联系方式）', '低（每项目一次）'],
            ['IT 运维', '部署和维护 Web 服务，确保服务可用', '低（部署时）'],
          ],
          [2500, 4026, 2500]
        ),
        new Paragraph({ spacing: { after: 120 } }),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('2.3 核心业务流程')] }),
        numItem('项目负责人提前创建券商函证模板，包括函证正文话术和项目组联系方式，设为默认模板'),
        numItem('项目组成员打开 Web 页面，系统自动加载默认模板'),
        numItem('项目组成员批量上传会所函证 PDF（拖拽或选择文件，最多 20 个）'),
        numItem('系统自动提取每个 PDF 的收函单位名称，标注识别置信度（已识别 / 需确认）'),
        numItem('项目组成员核对收函单位，对识别不准确的可手动修正或重新识别'),
        numItem('点击"全部生成"，系统逐份处理 PDF，完成后提供 ZIP 下载'),

        new Paragraph({ children: [new PageBreak()] }),

        // ====== 第3章 ======
        new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun('第3章 功能需求')] }),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('3.1 功能清单')] }),
        makeTable(
          ['编号', '功能模块', '功能描述', '优先级'],
          [
            ['F-01', 'PDF 头部替换', '自动白化 PDF 首页头部区域，重写为标准函证格式', 'P0 核心'],
            ['F-02', '格式自动提取', '自动识别原 PDF 的字号、边距、行距，保持排版一致', 'P0 核心'],
            ['F-03', '收函单位识别', '从 PDF 首页自动提取收函单位名称，支持置信度标注', 'P0 核心'],
            ['F-04', 'CJK 手动断行', '逐字符计算宽度进行中文换行，避免标点错位', 'P0 核心'],
            ['F-05', '表格区域保护', '通过关键词检测表格起始位置，确保表格数据不被修改', 'P0 核心'],
            ['F-06', '批量上传处理', '支持一次上传最多 20 个 PDF，批量处理后 ZIP 下载', 'P1 重要'],
            ['F-07', '配置模板管理', '创建/保存/加载/删除函证话术模板，支持设为默认', 'P1 重要'],
            ['F-08', '覆盖区域校准', '上传样本 PDF 预览，拖拽调整白化覆盖范围', 'P2 有用'],
            ['F-09', 'Web 操作界面', '浏览器访问的完整操作界面，无需安装客户端', 'P0 核心'],
            ['F-10', 'CLI 命令行', '支持命令行模式处理单个 PDF 文件', 'P2 有用'],
          ],
          [1000, 2000, 4026, 2000]
        ),
        new Paragraph({ spacing: { after: 120 } }),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('3.2 F-01: PDF 头部替换')] }),
        new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun('3.2.1 功能描述')] }),
        p('系统读取原始会所函证 PDF，对其首页头部区域执行白化（白色矩形覆盖），然后按券商标准格式重新写入以下内容：'),
        bulletItem('标题行：「企业询证函」，居中显示，使用标题字号（通常比正文大 2-4pt）'),
        bulletItem('收函单位行：「致：{收函单位名称}」，左对齐，使用正文字号'),
        bulletItem('正文行：券商标准函证话术，首行两全角空格缩进，CJK 手动断行'),
        bulletItem('提示行：「若您方有相关本询证函函证事项问题，请直接联系下方项目联系人电话：」，使用 SimHei 黑体加粗'),
        bulletItem('联系方式区：两栏布局（项目联系人 / 项目联系人电话、收件人 / 收件人电话、邮箱单独一行）'),

        new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun('3.2.2 处理流程')] }),
        imgPara('03_pdf_process.png', 4500000, 3654109),
        figCaption('2', 'PDF 处理四步流程'),

        new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun('3.2.3 白化策略')] }),
        p('白化区域为 PDF 首页从顶部 (y=0) 到 whiteout_bottom 坐标的矩形区域。whiteout_bottom 的确定规则：'),
        bulletItem('自动模式（默认）：取「新内容最底端 + 30pt」和「表格保护线 - 3pt」的较小值，确保不覆盖表格'),
        bulletItem('手动模式：用户在校准页面拖拽确定的固定 Y 坐标值，所有文件统一使用'),
        bulletItem('白化上限：不超过页面高度的 55%，防止过度覆盖'),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('3.3 F-02: 格式自动提取')] }),
        p('系统在替换前自动分析原 PDF 首页的排版参数，确保替换后的 PDF 在视觉上与原文件保持一致。'),
        makeTable(
          ['参数', '提取方式', '用途'],
          [
            ['正文字号 (bodySize)', '统计 y ∈ [50, 270] 区域字符尺寸众数', '正文、收函单位、提示行字号'],
            ['标题字号 (titleSize)', '正文字号 + 4pt', '标题行字号'],
            ['左边距 (leftMargin)', '正文区域字符最小 x 值', '所有文本左对齐基准'],
            ['段落宽度 (paraWidth)', '页面宽度 - 左边距 × 2', 'CJK 断行宽度限制'],
            ['行间距 (lineSpacing)', '相邻行 y 差值的平均', '各文本行垂直间距'],
            ['表格起始 Y (tableY)', '关键词匹配 + 数字序号检测', '白化下界上限'],
          ],
          [2200, 3400, 3426]
        ),
        new Paragraph({ spacing: { after: 120 } }),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('3.4 F-03: 收函单位识别')] }),
        p('系统在 PDF 首页 y ∈ [50, 300] 范围内搜索收函单位名称，使用多优先级策略：'),
        new Paragraph({ spacing: { after: 120 } }),

        imgPara('05_recipient.png', 4500000, 3790036),
        figCaption('3', '收函单位提取策略'),

        makeTable(
          ['优先级', '匹配策略', '说明'],
          [
            ['P1', '"单位：XXX"', '匹配包含"单位："的行，取冒号后全部内容'],
            ['P2', '"致：XXX"', '匹配包含"致："的行，取冒号后全部内容'],
            ['P3a', '敬启者同行', '匹配包含"敬启者"的行，取之前或之后的公司名'],
            ['P3b', '敬启者邻行', '向"敬启者"前后各搜索 3 行，找符合公司名特征的行'],
            ['P4', '区域搜索', '搜索范围内第一个符合公司名特征的行'],
            ['兜底', '返回空', '无匹配结果时返回空字符串，置信度 low'],
          ],
          [1200, 2500, 5326]
        ),
        new Paragraph({ spacing: { after: 60 } }),
        p('公司名特征判断：长度 4-30 字符、中文占比 ≥ 50% 且 ≥ 4 个中文字符、排除关键词（询证函、会计师、编号、索引、列示、余额等）。'),
        new Paragraph({ spacing: { after: 60 } }),
        p('前端展示：high/medium 置信度 → 绿色边框「已识别」；low 置信度 → 黄色边框「需确认」，支持手动编辑。'),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('3.5 F-04: CJK 手动断行')] }),
        p('因 PyMuPDF / PDFBox 自动换行时可能在中文字符边界产生标点错位（如行首出现逗号、句号），系统实现了自定义 CJK 断行算法：'),
        bulletItem('全角字符（中文、全角标点、全角字母）宽度 = 1 个字号单位（1 em）'),
        bulletItem('半角 ASCII 字符宽度 = 字号 × 0.55'),
        bulletItem('逐字符累加宽度，超出段落宽度时在当前字符前断行'),
        bulletItem('保证每行不超出段落宽度，标点不会孤立在行首'),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('3.6 F-05: 表格区域保护')] }),
        p('白化区域不得覆盖 PDF 首页的科目表格。系统通过以下关键词检测表格起始位置：'),
        bulletItem('直接关键词：「往来余额」「往来款项」「列示如下」「余额列示」「往来账项」'),
        bulletItem('兜底匹配：数字序号起始行（正则 ^\\d+[\\.\\s、]），如「1. 银行存款」'),
        p('检测到表格起始行后，将白化下界设为该行 y 坐标 - 3pt，确保表格数据完整保留。'),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('3.7 F-06: 批量上传处理')] }),
        bulletItem('支持拖拽上传或点击选择多个 PDF 文件（限制单次最多 20 个）'),
        bulletItem('上传后自动对每个文件提取收函单位，生成缩略图预览'),
        bulletItem('用户可对单个文件重新识别收函单位，或手动编辑'),
        bulletItem('点击"全部生成"后逐份处理，显示处理进度和结果'),
        bulletItem('处理完成后提供 ZIP 下载链接，支持重复下载'),
        bulletItem('会话在内存中管理，服务重启后丢失'),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('3.8 F-07/08: 配置模板管理 & 覆盖区域校准')] }),
        imgPara('04_config_flow.png', 5700000, 1331496),
        figCaption('4', '配置管理流程'),

        bulletItem('配置以 JSON 文件持久化到 configs/ 目录'),
        bulletItem('每套配置包含：名称、函证正文、联系方式（5 字段）、覆盖下界'),
        bulletItem('支持多套配置共存，_default.txt 记录默认配置名'),
        bulletItem('覆盖区域校准：上传样本 PDF → 生成预览 PNG → 显示红色遮罩 → 鼠标拖拽调整覆盖下界'),
        bulletItem('可强制指定覆盖下界值（所有文件统一），也可设为自动检测（每个文件各自计算）'),
        bulletItem('配置面板默认折叠，日常操作只需上传 → 生成，配置修改点击展开即可'),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('3.9 F-09: Web 操作界面')] }),
        p('Web 界面采用单页应用设计，主要页面：'),
        makeTable(
          ['页面/路由', '说明', '定位'],
          [
            ['/upload', 'PDF 格式转函主页面，自动加载默认配置，支持配置编辑 + 批量上传 + 生成下载', '日常使用'],
            ['/config', '独立配置管理页面（保留兼容）', '备用'],
            ['/', '单文件处理页面（旧版兼容）', '已废弃'],
            ['/batch', '301 重定向到 /upload', '兼容旧链接'],
          ],
          [1800, 5026, 2200]
        ),
        new Paragraph({ spacing: { after: 60 } }),
        p('设计原则：'),
        bulletItem('配置与操作分离：高频操作（上传、生成）在 /upload 主页；低频操作（模板编辑、区域校准）在折叠面板中'),
        bulletItem('默认配置自动加载：每次进入 /upload 自动调用 /config/default 填入模板字段'),
        bulletItem('用户可临时调整（不持久化）：在上传页修改的内容不会自动保存到模板配置'),
        bulletItem('单页操作：用户无需在多个页面间跳转'),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('3.10 F-10: CLI 命令行')] }),
        p('提供命令行模式处理单个 PDF 文件，适用于脚本化/自动化场景：'),
        new Paragraph({
          spacing: { before: 80, after: 80 },
          indent: { left: 720 },
          children: [new TextRun({ text: 'java -jar hanzheng-pdf-tool.jar 会所函证.pdf -t 话术.txt -o 输出.pdf', size: 20, font: 'Courier New', color: '555555' })],
        }),
        p('话术文件格式：TITLE: 行指定标题，正文内容，--- 分隔线以下为联系方式。'),

        new Paragraph({ children: [new PageBreak()] }),

        // ====== 第4章 ======
        new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun('第4章 系统架构')] }),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('4.1 整体架构')] }),
        imgPara('02_architecture.png', 4500000, 2924402),
        figCaption('5', '系统架构图'),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('4.2 技术栈')] }),
        makeTable(
          ['层级', '技术', '版本 / 说明'],
          [
            ['Web 服务', 'Python Flask', '端口 8888，单文件 web_form_server.py'],
            ['PDF 处理引擎', 'PyMuPDF (fitz) + pdfplumber', 'replace_header_v4.py，CJK 断行 + 白化 + 重写'],
            ['备选引擎', 'Java + Apache PDFBox', '端口 8889，JDK 内置 HttpServer'],
            ['前端', '纯 HTML/CSS/JS', '单页应用 upload.html'],
            ['配置持久化', 'JSON 文件', 'configs/ 目录 + _default.txt'],
            ['字体', 'NSimSun.ttf + SimHei.ttf', '宋体正文 + 黑体加粗'],
            ['Python 环境', 'Conda pytorch 环境', 'Python 3.x + Flask + PyMuPDF + pdfplumber'],
            ['Java 环境', 'JDK 1.8 + Maven', 'PDFBox 2.0.27'],
          ],
          [2000, 3000, 4026]
        ),
        new Paragraph({ spacing: { after: 120 } }),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('4.3 页面结构 (upload.html)')] }),
        p('主页面 /upload 采用单页全功能设计，包含三个功能区：'),
        bulletItem('配置信息条（顶部）：显示当前默认配置名称和覆盖下界，点击可展开配置编辑面板'),
        bulletItem('配置编辑面板（折叠）：子区域 1「覆盖区域校准」+ 子区域 2「模板内容编辑 + 保存/加载/删除」'),
        bulletItem('批量操作区：文件拖拽上传区 + 文件列表（含缩略图、收函单位、状态）+ 全部生成按钮 + 下载链接'),
        p('页面加载时自动调用 GET /config/default 填入默认模板，用户可在表单中临时修改后直接使用。'),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('4.4 PDF 引擎核心模块')] }),
        makeTable(
          ['模块/函数', '功能', '输入', '输出'],
          [
            ['extract_format()', '提取 PDF 格式参数', 'PDF 路径', 'PdfFormat 对象'],
            ['extract_recipient()', '自动提取收函单位', 'PDF 路径', '(名称, 置信度)'],
            ['_find_table_start()', '检测表格保护线 Y 坐标', '行分组 + Y 列表', 'float: tableY'],
            ['wrap_cjk_text()', 'CJK 手动断行', '(文本, 字号, 宽度)', 'list[str]: 行列表'],
            ['process_from_data()', '结构化处理入口 (Web)', '路径 + 参数 dict', '(success, message)'],
            ['process_page()', '单页处理核心', 'PDF 文档 + 格式 + 内容', '无 (修改 in-place)'],
          ],
          [2200, 2200, 2200, 2426]
        ),
        new Paragraph({ spacing: { after: 120 } }),

        new Paragraph({ children: [new PageBreak()] }),

        // ====== 第5章 ======
        new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun('第5章 接口设计')] }),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('5.1 API 总览')] }),
        makeTable(
          ['方法', '路由', '功能说明'],
          [
            ['GET', '/upload', '主页面（自动加载默认配置）'],
            ['GET', '/', '单文件处理页（旧版兼容）'],
            ['GET', '/batch', '301 重定向到 /upload'],
            ['GET', '/config', '配置管理页面（兼容保留）'],
            ['POST', '/config/preview', '上传样本 PDF，返回预览图 + 表格线'],
            ['POST', '/config/save', '保存配置（JSON 持久化）'],
            ['GET', '/config/list', '列出所有配置'],
            ['GET', '/config/load/<name>', '加载指定配置'],
            ['DELETE', '/config/delete/<name>', '删除指定配置'],
            ['GET', '/config/default', '获取默认配置'],
            ['POST', '/batch/upload', '批量上传 PDF，自动提取收函单位'],
            ['POST', '/batch/generate', '批量生成替换后的 PDF'],
            ['GET', '/batch/download/<token>', '下载 ZIP'],
            ['POST', '/batch/re-recognize', '重新识别单个文件收函单位'],
            ['GET', '/batch/preview/<token>/<file>', '文件缩略图'],
          ],
          [1200, 3200, 4626]
        ),
        new Paragraph({ spacing: { after: 200 } }),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('5.2 核心接口详情')] }),

        new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun('5.2.1 POST /batch/upload — 批量上传')] }),
        p('请求：multipart/form-data，字段 pdf_files（可多个 PDF 文件，最多 20 个）。'),
        new Paragraph({ spacing: { after: 60 } }),
        p('响应示例：'),
        new Paragraph({
          spacing: { before: 60, after: 120 },
          indent: { left: 720 },
          children: [
            new TextRun({ text: '{\n  "success": true,\n  "token": "a1b2c3d4e5f6",\n  "files": [{\n    "id": "abc12345",\n    "name": "立信函证.pdf",\n    "recipient": "A公司",\n    "confidence": "high",\n    "previewUrl": "/batch/preview/a1b2c3d4e5f6/thumb_abc12345.png"\n  }]\n}', size: 18, font: 'Courier New', color: '555555' }),
          ],
        }),

        new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun('5.2.2 POST /batch/generate — 批量生成')] }),
        p('请求：JSON body。'),
        new Paragraph({
          spacing: { before: 60, after: 60 },
          indent: { left: 720 },
          children: [
            new TextRun({ text: '{\n  "token": "a1b2c3d4e5f6",\n  "body_text": "...",\n  "contacts": { "项目联系人": "...", ... },\n  "recipients": { "abc12345": "A公司" },\n  "whiteout_bottom": 318.5\n}', size: 18, font: 'Courier New', color: '555555' }),
          ],
        }),
        p('说明：whiteout_bottom 为 null 时使用自动检测；recipients 覆盖前端手动修改的收函单位。'),
        new Paragraph({ spacing: { after: 60 } }),
        p('响应示例：'),
        new Paragraph({
          spacing: { before: 60 },
          indent: { left: 720 },
          children: [
            new TextRun({ text: '{\n  "success": true,\n  "downloadToken": "a1b2c3d4e5f6",\n  "total": 5,\n  "errors": [{"file": "xxx.pdf", "error": "收函单位为空"}]\n}', size: 18, font: 'Courier New', color: '555555' }),
          ],
        }),

        new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun('5.2.3 POST /config/save — 保存配置')] }),
        p('请求：JSON body。'),
        new Paragraph({
          spacing: { before: 60 },
          indent: { left: 720 },
          children: [
            new TextRun({ text: '{\n  "name": "开源证券-默认模板",\n  "body_text": "本公司聘请的XX证券...",\n  "contacts": { "项目联系人":"张三", ... },\n  "whiteout_bottom": 318.5,\n  "is_default": true\n}', size: 18, font: 'Courier New', color: '555555' }),
          ],
        }),
        new Paragraph({ spacing: { after: 200 } }),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('5.3 配置 JSON 结构')] }),
        new Paragraph({
          spacing: { before: 80, after: 120 },
          indent: { left: 720 },
          children: [
            new TextRun({ text: '{\n  "name": "开源证券-默认模板",\n  "body_text": "本公司聘请的XX证券股份有限公司正在对本公司进行尽职调查...",\n  "contacts": {\n    "项目联系人": "张三",\n    "项目联系人电话": "13800138000",\n    "收件人": "开源证券投行部",\n    "收件人电话": "0755-88888888",\n    "邮箱": "touhang@kzq.com.cn"\n  },\n  "whiteout_bottom": 318.5\n}', size: 18, font: 'Courier New', color: '555555' }),
          ],
        }),
        p('说明：whiteout_bottom 为 null 时表示「自动检测」模式，不为 null 时为强制指定值。'),

        new Paragraph({ children: [new PageBreak()] }),

        // ====== 第6章 ======
        new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun('第6章 数据设计')] }),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('6.1 配置数据')] }),
        p('存储位置：python/configs/ 目录。'),
        makeTable(
          ['文件', '格式', '说明'],
          [
            ['_default.txt', '纯文本', '仅存一行：默认配置的安全文件名（不含 .json）'],
            ['{名称}.json', 'JSON', '单个配置模板，结构见 5.3 节'],
          ],
          [2000, 1200, 5826]
        ),
        new Paragraph({ spacing: { after: 120 } }),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('6.2 批量会话数据')] }),
        p('存储方式：Python 内存字典 _batch_sessions（服务重启丢失）。'),
        makeTable(
          ['字段', '类型', '说明'],
          [
            ['token', 'string (12 hex)', '会话唯一标识'],
            ['files[]', 'array', '文件列表，每项含 id/name/path/recipient/confidence'],
            ['status', 'string', '会话状态：uploaded / done'],
            ['zip_path', 'string', '生成 ZIP 文件的绝对路径'],
          ],
          [2000, 2000, 5026]
        ),
        new Paragraph({ spacing: { after: 120 } }),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('6.3 文件存储')] }),
        makeTable(
          ['目录/文件', '用途', '生命周期'],
          [
            ['python/outputs/', '单文件处理输出 PDF', '持久化（手动清理）'],
            ['临时目录 (TEMP_DIR)', '上传临时 PDF、批量输出、ZIP、预览 PNG', '服务重启后系统清理'],
            ['python/configs/', '模板配置 JSON + _default.txt', '持久化'],
            ['python/fonts/', 'NSimSun.ttf 字体文件', '持久化'],
          ],
          [2500, 3526, 3000]
        ),

        new Paragraph({ children: [new PageBreak()] }),

        // ====== 第7章 ======
        new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun('第7章 非功能性需求')] }),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('7.1 性能需求')] }),
        makeTable(
          ['指标', '要求'],
          [
            ['单文件处理时间', '≤ 5 秒（含格式提取 + 白化 + 重写）'],
            ['批量处理吞吐量', '20 个文件 ≤ 2 分钟'],
            ['PDF 预览图生成', '150 DPI 预览 ≤ 3 秒，72 DPI 缩略图 ≤ 1 秒'],
            ['并发支持', '单线程 Flask，同一时刻处理一个请求'],
            ['最大上传大小', '32 MB（Flask MAX_CONTENT_LENGTH）'],
          ],
          [2800, 6226]
        ),
        new Paragraph({ spacing: { after: 120 } }),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('7.2 兼容性需求')] }),
        bulletItem('浏览器：Chrome 80+、Edge 80+（无需 IE 支持）'),
        bulletItem('操作系统：Windows 10/11（服务端）、浏览器端跨平台'),
        bulletItem('PDF 格式：支持 PDF 1.4 - 2.0 各版本'),
        bulletItem('字体回退：NSimSun.ttf 缺失时自动查找系统宋体 (simsun.ttc)'),
        bulletItem('双版本保障：Python 版故障时可切换到 Java 版（端口 8889）'),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('7.3 安全性需求')] }),
        bulletItem('文件名清洗：所有用户输入的文件名经过安全清洗（仅保留字母数字和 _-）'),
        bulletItem('路径穿越防护：下载接口使用 os.path.basename 防目录遍历'),
        bulletItem('上传限制：单文件不超过 32 MB，单次批量不超过 20 个文件'),
        bulletItem('文件类型校验：仅接受 .pdf 扩展名文件'),
        bulletItem('临时文件隔离：使用 tempfile.mkdtemp 创建独立临时目录'),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('7.4 可维护性需求')] }),
        bulletItem('Python 版所有代码集中在 2 个 .py 文件 + 3 个 .html 模板，结构清晰'),
        bulletItem('配置 JSON 格式可读、可手动编辑'),
        bulletItem('内存日志：batch/generate 接口返回每个文件的处理结果和错误详情'),
        bulletItem('Java 版与 Python 版核心逻辑保持一致，变更需同步'),

        new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun('7.5 部署需求')] }),
        bulletItem('Python 环境：Conda pytorch 环境，需安装 flask、PyMuPDF、pdfplumber'),
        bulletItem('启动命令：python web_form_server.py（端口 8888）'),
        bulletItem('Java 备选：双击 run.bat 启动（端口 8889，需 JDK 1.8 + PDFBox 2.0.27）'),
        bulletItem('字体文件：NSimSun.ttf 放入 python/fonts/ 或 java/fonts/ 目录'),
        bulletItem('推荐通过外部系统（如"往来询证"系统）以链接方式集成：http://<host>:8888/upload'),

      ],
    },
  ],
});

// Write
const outPath = path.join(OUT_DIR, '函证PDF头部替换工具_需求规格说明书_v3.0_Visio版.docx');
Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync(outPath, buf);
  console.log('文档已生成:', outPath);
}).catch(err => {
  console.error('生成失败:', err);
});

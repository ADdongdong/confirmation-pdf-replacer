package com.hanzheng.core;

import com.hanzheng.model.HanzhengRequest;
import com.hanzheng.model.PdfFormat;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.awt.Color;
import java.io.*;
import java.util.*;

/**
 * PDF 核心处理器
 *
 * 功能：
 *  1. 白化原始 PDF 头部区域（覆盖白色矩形）
 *  2. 按布局重写：
 *     - 标题行（居中、大字号）
 *     - 致函单位行 + 正文（左对齐）
 *     - 加粗提示行（伪加粗：双重渲染）
 *     - 联系方式（两栏布局，行距与正文一致）
 *  3. 保持表格区域完全不动
 */
public class PdfProcessor {

    /** 正文首行缩进：两个全角空格 */
    private static final String INDENT = "\u3000\u3000";

    /** 加粗提示行固定文案 */
    private static final String PROMPT_LINE = "若您方有相关本询证函函证事项问题，请直接联系下方项目联系人电话：";

    /** 两栏布局间距 (pt) */
    private static final double COL_GAP = 20;

    public PdfProcessor() {}

    /**
     * 执行 PDF 头部替换
     *
     * @param req  请求参数（包含输入输出路径、函证内容等）
     * @return 成功消息，失败时通过 IOException 抛出
     */
    public String process(HanzhengRequest req) throws Exception {
        // ---- 1. 提取格式 ----
        FormatExtractor extractor = new FormatExtractor();
        PdfFormat fmt = extractor.extract(req.inputPath);

        // ---- 2. 查找字体 ----
        File fontFile = findFontFile(req.fontFile);
        File boldFontFile = findBoldFontFile();

        // ---- 3. 处理 PDF ----
        PDDocument doc = null;
        try {
            doc = PDDocument.load(new File(req.inputPath));
            if (doc.getNumberOfPages() == 0) {
                throw new IOException("PDF 无页面");
            }

            double userWhiteout = (req.whiteoutBottom != null && req.whiteoutBottom > 0)
                    ? req.whiteoutBottom : -1;
            processPage(doc, req, fmt, fontFile, boldFontFile, userWhiteout);

            // 确保输出目录存在
            File outFile = new File(req.outputPath);
            File outDir = outFile.getParentFile();
            if (outDir != null && !outDir.exists()) {
                outDir.mkdirs();
            }

            doc.save(outFile);
            return req.outputPath;

        } finally {
            if (doc != null) {
                doc.close();
            }
        }
    }

    /**
     * 处理第一页
     */
    private void processPage(PDDocument doc, HanzhengRequest req,
                              PdfFormat fmt, File fontFile, File boldFontFile,
                              double userWhiteoutBottom) throws IOException {
        PDPage page = doc.getPage(0);

        double left = fmt.leftMargin;
        double paraW = fmt.paraWidth;
        double bodyFs = fmt.bodySize;
        double titleFs = fmt.titleSize;
        double spacing = fmt.lineSpacing;
        double tableY = fmt.tableY;
        double pageW = fmt.pageWidth;

        // ---- Phase 1: 计算布局 ----

        // 构建头部行列表：(text, fontSize, align)
        // align: 0=center, 1=right, 2=left
        List<TextBlock> headerBlocks = new ArrayList<TextBlock>();
        double y = 48.0;

        // 标题行（居中）
        headerBlocks.addAll(wrapText("企业询证函", titleFs, paraW, pageW, 0));
        y += spacing; // 标题行占位，但实际不同字号间距特殊处理

        // 正文行
        String companyLine = "致：" + (req.companyName != null ? req.companyName.trim() : "");
        String bodyLine = INDENT + (req.bodyText != null ? req.bodyText.trim() : "");

        // 重新计算 y，标题结束后开始
        y = 48.0 + titleFs + 5 + spacing; // 标题一行占用

        // 致函单位行
        headerBlocks.add(new TextBlock(companyLine, bodyFs, 2)); // left align
        y += spacing;

        // 正文（需断行）
        List<String> bodyWrapped = CjkWrapper.wrapCjkText(bodyLine, bodyFs, paraW);
        for (String sub : bodyWrapped) {
            headerBlocks.add(new TextBlock(sub, bodyFs, 2));
            y += spacing;
        }

        double headerEndY = y + 6;

        // 联系方式起始
        double contactStartY = Math.max(headerEndY + 4, 188);
        double maxContactEnd = tableY - 5;

        // 联系人行数
        int contactCount = 0;
        if (req.contactPerson != null && !req.contactPerson.trim().isEmpty()) contactCount++;
        if (req.recipient != null && !req.recipient.trim().isEmpty()) contactCount++;
        if (req.email != null && !req.email.trim().isEmpty()) contactCount++;

        int totalLines = 1 /* 提示行 */ + contactCount;
        double contactGap = spacing; // 联系方式行距与正文一致
        double availForContact = maxContactEnd - contactStartY;
        if (contactGap * totalLines > availForContact) {
            contactGap = Math.max(availForContact / totalLines, 12);
        }

        // 提示行（与“致：xxxx公司”行左对齐，不再缩进）
        List<String> promptWrapped = CjkWrapper.wrapCjkText(PROMPT_LINE, bodyFs, paraW);
        double yc = contactStartY;

        // 联系方式两栏布局
        double colWidth = (paraW - COL_GAP) / 2;
        double rightX = left + colWidth + COL_GAP;

        // ---- 待写入的文本块列表 ----
        List<PlacedText> allPlaced = new ArrayList<PlacedText>();

        // 头部块
        double hy = 48.0;
        double nextHy = 48.0 + titleFs + 5 + spacing; // 第一行是标题，之后开始

        // 标题：居中 + 加粗
        allPlaced.add(new PlacedText("企业询证函", 0, hy, pageW, hy + titleFs + 5,
                                      titleFs, 0 /* center */, true /* bold */));

        // 致函单位 + 正文
        hy = nextHy;
        allPlaced.add(new PlacedText(companyLine, left, hy, left + paraW, hy + bodyFs + 4,
                                      bodyFs, 2 /* left */));
        hy += spacing;
        for (String sub : bodyWrapped) {
            allPlaced.add(new PlacedText(sub, left, hy, left + paraW, hy + bodyFs + 4,
                                          bodyFs, 2));
            hy += spacing;
        }
        headerEndY = hy + 6;

        // 重新计算联系方式区域
        contactStartY = Math.max(headerEndY + 4, 188);
        maxContactEnd = tableY - 5;
        availForContact = maxContactEnd - contactStartY;
        totalLines = 1 + contactCount;
        contactGap = spacing; // 联系方式行距与正文一致
        if (contactGap * totalLines > availForContact) {
            contactGap = Math.max(availForContact / totalLines, 12);
        }

        // 提示行（加粗）
        yc = contactStartY;
        for (String sub : promptWrapped) {
            allPlaced.add(new PlacedText(sub, left, yc, left + paraW, yc + bodyFs + 4,
                                          bodyFs, 2, true /* bold */));
            yc += contactGap;
        }

        // 联系方式
        // 两栏配对
        String[][] pairs = {
            {req.contactPerson, req.contactPhone, "项目联系人", "项目联系人电话"},
            {req.recipient, req.recipientPhone, "收件人", "收件人电话"},
        };

        for (String[] pair : pairs) {
            if (yc + contactGap > maxContactEnd + 4) break;

            String leftVal = (pair[0] != null ? pair[0].trim() : "");
            String rightVal = (pair[1] != null ? pair[1].trim() : "");
            if (leftVal.isEmpty() && rightVal.isEmpty()) continue;

            if (!leftVal.isEmpty()) {
                String leftText = pair[2] + "：" + leftVal;
                allPlaced.add(new PlacedText(leftText, left, yc, left + colWidth,
                                              yc + bodyFs + 4, bodyFs, 2));
            }
            if (!rightVal.isEmpty()) {
                String rightText = pair[3] + "：" + rightVal;
                allPlaced.add(new PlacedText(rightText, rightX, yc, left + paraW,
                                              yc + bodyFs + 4, bodyFs, 2));
            }
            yc += contactGap;
        }

        // 邮箱单独一行
        String emailVal = (req.email != null ? req.email.trim() : "");
        if (!emailVal.isEmpty() && yc + contactGap <= maxContactEnd + 4) {
            String emailText = "邮箱：" + emailVal;
            allPlaced.add(new PlacedText(emailText, left, yc, left + paraW,
                                          yc + bodyFs + 4, bodyFs, 2));
            yc += contactGap;
        }

        // 提前获取页面高度（后续用于坐标转换）
        float pageHeight = page.getMediaBox().getHeight();

        // 表格起始 Y（PDF 坐标，从底部算起）→ 转换为布局坐标（从顶部算起）
        // 即：表格距离页面顶部的距离 = pageHeight - tableY
        double maxWhiteoutFromTop = pageHeight - tableY - 3;

        // 使用用户指定值，否则自动计算
        // 注意：所有 whiteoutBottom 均使用「从顶部算起」的布局坐标
        double whiteoutBottom;
        if (userWhiteoutBottom > 0) {
            whiteoutBottom = Math.min(userWhiteoutBottom, maxWhiteoutFromTop);
            System.out.println("  白色覆盖区域: 用户指定 顶部" + Math.round(userWhiteoutBottom)
                    + "pt → 实际顶部" + Math.round(whiteoutBottom)
                    + "pt (表格位于顶部" + Math.round(maxWhiteoutFromTop) + "pt)");
        } else {
            whiteoutBottom = Math.min(Math.max(headerEndY, yc + 30), maxWhiteoutFromTop);
            System.out.println("  白色覆盖区域: 自动检测 顶部" + Math.round(whiteoutBottom)
                    + "pt (表格位于顶部" + Math.round(maxWhiteoutFromTop) + "pt)");
        }

        // ---- Phase 2: 执行操作 ----

        // 1) 加载中文字体（常规 + 粗体）
        PDType0Font font = PDType0Font.load(doc, fontFile);
        PDType0Font boldFont = PDType0Font.load(doc, boldFontFile);

        // 2) 绘制白色遮罩矩形覆盖头部区域
        // 布局坐标（从顶部）→ PDF 坐标（从底部）：pdfY = pageHeight - whiteoutBottom
        float pdfWhiteY = pageHeight - (float) whiteoutBottom;
        PDPageContentStream cs = new PDPageContentStream(doc, page,
                PDPageContentStream.AppendMode.APPEND, true);
        try {
            // 白色填充矩形：从 PDF y=pdfWhiteY 到 PDF y=pageHeight（即布局顶部到 whiteoutBottom）
            cs.setNonStrokingColor(Color.WHITE);
            cs.addRect(0, pdfWhiteY, (float) pageW, (float) whiteoutBottom);
            cs.fill();

            // 3) 逐块写入文本
            cs.setNonStrokingColor(Color.BLACK);

            for (PlacedText pt : allPlaced) {
                PDType0Font useFont = pt.bold ? boldFont : font;
                writeTextBlock(cs, useFont, pt, page);
            }

        } finally {
            cs.close();
        }

        System.out.println("  页面高度: " + Math.round(pageHeight));
        System.out.println("  白化区域: 顶部 0 → 顶部 " + Math.round(whiteoutBottom)
                + "pt (PDF y=" + Math.round(pdfWhiteY) + " → y=" + Math.round(pageHeight) + ")");
        System.out.println("  写入内容: " + allPlaced.size() + " 块");
    }

    /**
     * 写入单块文本（支持对齐：0=居中, 1=右, 2=左）
     */
    private void writeTextBlock(PDPageContentStream cs, PDType0Font font,
                                 PlacedText pt, PDPage page) throws IOException {
        // 清理文本：去除换行符等不可渲染字符
        String cleanText = pt.text.replace("\r", "").replace("\n", " ").trim();
        if (cleanText.isEmpty()) {
            return; // 空文本不渲染
        }

        float fontSize = (float) pt.fontSize;
        float textWidth = 0;
        try {
            textWidth = font.getStringWidth(cleanText) / 1000f * fontSize;
        } catch (Exception e) {
            textWidth = cleanText.length() * fontSize * 0.55f;
        }

        float x;
        if (pt.align == 0) {
            // 居中
            float pageWidth = page.getMediaBox().getWidth();
            x = (pageWidth - textWidth) / 2f;
        } else if (pt.align == 1) {
            // 右对齐
            x = (float) (pt.x + pt.width - textWidth);
        } else {
            // 左对齐
            x = (float) pt.x;
        }

        // PDF 坐标系：原点在左下角，y 轴向上
        float pageHeight = page.getMediaBox().getHeight();
        float pdfY = pageHeight - (float) pt.y - fontSize;

        cs.setFont(font, fontSize);
        cs.beginText();
        cs.newLineAtOffset(x, pdfY);
        try {
            cs.showText(cleanText);
        } finally {
            cs.endText();
        }
    }


    // 注：不再使用伪加粗。改用真正的粗体字体 (simsunb.ttf / simhei.ttf) 通过 writeTextBlock 渲染，
    // 避免"两次绘制偏移"造成的重影问题。详见 findBoldFontFile()。


    /**
     * 将单行文本包装为 TextBlock 列表（标题不需要断行）
     */
    private List<TextBlock> wrapText(String text, double fontSize, double paraW,
                                      double pageW, int align) {
        List<TextBlock> blocks = new ArrayList<TextBlock>();
        TextBlock tb = new TextBlock(text, fontSize, align);
        blocks.add(tb);
        return blocks;
    }

    /**
     * 查找字体文件（常规字重）
     */
    private File findFontFile(String specifiedPath) {
        // 优先使用指定路径
        if (specifiedPath != null) {
            File f = new File(specifiedPath);
            if (f.exists()) return f;
        }

        // 按优先级查找
        String[] candidates = {
            "fonts/NSimSun.ttf",
            "fonts/simsun.ttc",
            "C:/Windows/Fonts/simsun.ttc",
            "C:/Windows/Fonts/simsun.ttf",
            "C:/Windows/Fonts/msyh.ttc",
            "C:/Windows/Fonts/msyh.ttf",
        };

        for (String path : candidates) {
            File f = new File(path);
            if (f.exists()) {
                System.out.println("  正文字体: " + f.getAbsolutePath());
                return f;
            }
        }

        throw new RuntimeException("未找到中文字体文件，请将 NSimSun.ttf 放到 fonts/ 目录下" +
                "或放置到 C:/Windows/Fonts/ 中");
    }

    /**
     * 查找粗体字体文件
     *
     * 注意：Windows 上的 simsunb.ttf 实际上是 "SimSun-ExtB"（SimSun 扩展B区），
     *       不包含常用汉字（如"企"、"业"），加载后会抛 "No glyph for U+XXXX"。
     *       所以必须优先使用 simhei.ttf（SimHei 黑体），它是完整的字库。
     */
    private File findBoldFontFile() {
        // 按优先级查找真正的粗体字体
        String[] candidates = {
            "C:/Windows/Fonts/simhei.ttf",    // SimHei 黑体（完整字库）← 首选
            "fonts/simhei.ttf",
            "C:/Windows/Fonts/simfang.ttf",   // 仿宋
            "fonts/simfang.ttf",
            "C:/Windows/Fonts/simsunb.ttf",   // 注意：这其实是 SimSun-ExtB，不可作为粗体
            "fonts/simsunb.ttf",
        };

        for (int i = 0; i < candidates.length; i++) {
            File f = new File(candidates[i]);
            if (f.exists()) {
                // 前两个（simhei）是完整字库
                if (i >= 4) {
                    System.out.println("  警告: 加载 " + f.getName() + "，这其实是 SimSun-ExtB（扩展B区），可能缺少常用字");
                }
                System.out.println("  粗体字体: " + f.getAbsolutePath());
                return f;
            }
        }

        // 粗体不可用时回退到正文字体
        System.out.println("  警告: 未找到粗体字体 (simhei.ttf/simfang.ttf)，将回退到正文字体");
        return findFontFile(null);
    }

    // ================================================================
    // 内部类
    // ================================================================

    /** 布局计算阶段的文本块 */
    private static class TextBlock {
        final String text;
        final double fontSize;
        final int align; // 0=center, 1=right, 2=left

        TextBlock(String text, double fontSize, int align) {
            this.text = text;
            this.fontSize = fontSize;
            this.align = align;
        }
    }

    /** 最终写入阶段的定位文本 */
    private static class PlacedText {
        final String text;
        final double x, y, width, height;
        final double fontSize;
        final int align; // 0=center, 1=right, 2=left
        final boolean bold; // 是否加粗（伪加粗：双重渲染偏移）

        PlacedText(String text, double x, double y, double width, double height,
                   double fontSize, int align, boolean bold) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.fontSize = fontSize;
            this.align = align;
            this.bold = bold;
        }

        PlacedText(String text, double x, double y, double width, double height,
                   double fontSize, int align) {
            this(text, x, y, width, height, fontSize, align, false);
        }
    }
}

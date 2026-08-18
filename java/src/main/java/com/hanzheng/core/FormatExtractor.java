package com.hanzheng.core;

import com.hanzheng.model.PdfFormat;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * PDF 格式自动提取器
 *
 * 从原始会所函证 PDF 中自动提取：
 *   - 正文字号 / 标题字号
 *   - 左边距 / 段落宽度
 *   - 行间距
 *   - 表格起始坐标
 */
public class FormatExtractor extends PDFTextStripper {

    /** 所有字符的文本位置信息 */
    private List<TextPositionWithPage> allChars = new ArrayList<TextPositionWithPage>();

    public FormatExtractor() throws IOException {
        super();
        // 禁用自动排序，我们自行处理
    }

    /**
     * 内部类：记录字符位置和所在页面
     */
    private static class TextPositionWithPage {
        final float x, y, width, height, fontSize;
        final String unicode;
        final int pageIndex;

        TextPositionWithPage(TextPosition tp, int pageIndex) {
            this.x = tp.getX();
            this.y = tp.getY();
            this.width = tp.getWidth();
            this.height = tp.getHeightDir(); // 字体高度
            this.fontSize = tp.getFontSizeInPt();
            this.unicode = tp.getUnicode();
            this.pageIndex = pageIndex;
        }
    }

    @Override
    protected void processTextPosition(TextPosition text) {
        // 只处理第一页
        if (getCurrentPageNo() == 1) {
            allChars.add(new TextPositionWithPage(text, getCurrentPageNo() - 1));
        }
    }

    /**
     * 提取格式参数
     *
     * @param inputPath 原始 PDF 路径
     * @return PdfFormat 格式参数对象
     */
    public PdfFormat extract(String inputPath) throws IOException {
        allChars.clear();

        PDDocument doc = null;
        try {
            doc = PDDocument.load(new File(inputPath));
            if (doc.getNumberOfPages() == 0) {
                throw new IOException("PDF 无页面");
            }

            // 获取页面尺寸
            PDPage page = doc.getPage(0);
            float pageWidth = page.getMediaBox().getWidth();

            // 执行文本提取（触发 processTextPosition 回调）
            this.setSortByPosition(true);
            this.setStartPage(1);
            this.setEndPage(1);
            this.getText(doc);

            if (allChars.isEmpty()) {
                // 无法提取字符，使用默认参数
                return createDefaultFormat(pageWidth);
            }

            // ---- 按 y 坐标分组 ----
            Map<Integer, List<TextPositionWithPage>> lineGroups = new LinkedHashMap<Integer, List<TextPositionWithPage>>();
            for (TextPositionWithPage c : allChars) {
                int yKey = (int) (Math.round(c.y / 3) * 3);
                List<TextPositionWithPage> group = lineGroups.get(yKey);
                if (group == null) {
                    group = new ArrayList<TextPositionWithPage>();
                    lineGroups.put(yKey, group);
                }
                group.add(c);
            }

            List<Integer> sortedYs = new ArrayList<Integer>(lineGroups.keySet());
            Collections.sort(sortedYs);

            // ---- 字号统计 ----
            // 统计 y∈(50, 270) 范围内的字号
            Map<Float, Integer> sizeCounter = new HashMap<Float, Integer>();
            for (TextPositionWithPage c : allChars) {
                if (c.y > 50 && c.y < 270) {
                    float rounded = Math.round(c.fontSize * 10f) / 10f;
                    Integer count = sizeCounter.get(rounded);
                    sizeCounter.put(rounded, (count == null ? 0 : count) + 1);
                }
            }

            // 正文字号：选取 8~14pt 中出现次数最多的
            double bodySize = 10.5;
            double titleSize = 18.0;
            int maxBodyCount = 0;
            float maxSize = 0;
            for (Map.Entry<Float, Integer> e : sizeCounter.entrySet()) {
                float s = e.getKey();
                int n = e.getValue();
                if (s >= 8 && s <= 14 && n > maxBodyCount) {
                    maxBodyCount = n;
                    bodySize = s;
                }
                if (n > maxBodyCount) {
                    // titleSize: 出现最多的字号的均值
                }
                if (s > maxSize) {
                    maxSize = s;
                }
            }
            // 标题字号 = 最大字号
            titleSize = maxSize > 0 ? maxSize : 18.0;

            // ---- 边距计算 ----
            double leftMargin = 50;
            double rightEdge = 560;
            double minX = Double.MAX_VALUE;
            double maxX = 0;
            for (TextPositionWithPage c : allChars) {
                if (c.y > 50 && c.y < 260) {
                    if (c.x < minX) minX = c.x;
                    if (c.x + c.width > maxX) maxX = c.x + c.width;
                }
            }
            if (minX < Double.MAX_VALUE) {
                leftMargin = minX;
                rightEdge = maxX;
            }

            double paraWidth = pageWidth - leftMargin - (pageWidth - rightEdge) - 8;

            // ---- 行距计算 ----
            List<Double> gaps = new ArrayList<Double>();
            Integer prevY = null;
            for (int y : sortedYs) {
                if (y < 50 || y > 270) continue;
                if (prevY != null) {
                    double gap = y - prevY;
                    if (gap > 10 && gap < 40) {
                        gaps.add(gap);
                    }
                }
                prevY = y;
            }

            double lineSpacing = 15.7;
            if (!gaps.isEmpty()) {
                double sum = 0;
                for (double g : gaps) sum += g;
                lineSpacing = sum / gaps.size();
            }

            // ---- 表格起始检测 ----
            double tableY = findTableStart(lineGroups, sortedYs, 250);

            PdfFormat fmt = new PdfFormat();
            fmt.bodySize = bodySize;
            fmt.titleSize = titleSize;
            fmt.leftMargin = leftMargin;
            fmt.paraWidth = paraWidth;
            fmt.lineSpacing = lineSpacing;
            fmt.tableY = tableY;
            fmt.pageWidth = pageWidth;

            System.out.println("  原 PDF 格式:");
            System.out.println("    正文字号: " + round1(bodySize) + "pt    标题字号: " + round1(titleSize) + "pt");
            System.out.println("    左边距: " + Math.round(leftMargin) + "pt    段落宽度: " + Math.round(paraWidth) + "pt");
            System.out.println("    行间距: " + round1(lineSpacing) + "pt    表格起始: y=" + Math.round(tableY));

            return fmt;

        } finally {
            if (doc != null) {
                doc.close();
            }
        }
    }

    /**
     * 检测表格起始行（"列示如下"等关键词所在 y 坐标）
     */
    private double findTableStart(Map<Integer, List<TextPositionWithPage>> lineGroups,
                                   List<Integer> sortedYs, double minY) {
        String[] keywords = {"往来余额", "往来款项", "列示如下", "余额列示", "往来账项"};

        for (int y : sortedYs) {
            if (y < minY) continue;

            List<TextPositionWithPage> chars = lineGroups.get(y);
            // 按 x 排序拼接文本
            List<TextPositionWithPage> sorted = new ArrayList<TextPositionWithPage>(chars);
            Collections.sort(sorted, new Comparator<TextPositionWithPage>() {
                public int compare(TextPositionWithPage a, TextPositionWithPage b) {
                    return Float.compare(a.x, b.x);
                }
            });

            StringBuilder sb = new StringBuilder();
            for (TextPositionWithPage c : sorted) {
                sb.append(c.unicode);
            }
            String lineText = sb.toString();

            for (String kw : keywords) {
                if (lineText.contains(kw)) {
                    return y - 3;
                }
            }
        }

        // fallback：查找以数字开头的行
        Pattern numPattern = Pattern.compile("^\\d+[\\.\\s、]");
        for (int y : sortedYs) {
            if (y < minY) continue;

            List<TextPositionWithPage> chars = lineGroups.get(y);
            List<TextPositionWithPage> sorted = new ArrayList<TextPositionWithPage>(chars);
            Collections.sort(sorted, new Comparator<TextPositionWithPage>() {
                public int compare(TextPositionWithPage a, TextPositionWithPage b) {
                    return Float.compare(a.x, b.x);
                }
            });

            StringBuilder sb = new StringBuilder();
            for (TextPositionWithPage c : sorted) {
                sb.append(c.unicode);
            }
            if (numPattern.matcher(sb.toString().trim()).find()) {
                return y - 3;
            }
        }

        return 267;
    }

    /**
     * 创建默认格式（无法提取时回退）
     */
    private PdfFormat createDefaultFormat(double pageWidth) {
        PdfFormat fmt = new PdfFormat();
        fmt.bodySize = 10.5;
        fmt.titleSize = 18.0;
        fmt.leftMargin = 50;
        fmt.paraWidth = pageWidth - 50 - 50;
        fmt.lineSpacing = 15.7;
        fmt.tableY = 267;
        fmt.pageWidth = pageWidth;
        System.out.println("  ⚠ 无法提取原 PDF 格式，使用默认参数");
        return fmt;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}

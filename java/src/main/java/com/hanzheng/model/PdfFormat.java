package com.hanzheng.model;

/**
 * PDF 格式参数（从原 PDF 自动提取）
 */
public class PdfFormat {

    /** 正文字号 (pt) */
    public double bodySize;

    /** 标题字号 (pt) */
    public double titleSize;

    /** 左边距 (pt) */
    public double leftMargin;

    /** 段落宽度 (pt) */
    public double paraWidth;

    /** 行间距 (pt) */
    public double lineSpacing;

    /** 表格起始 y 坐标 (pt) */
    public double tableY;

    /** 页面宽度 */
    public double pageWidth;

    public PdfFormat() {}

    @Override
    public String toString() {
        return String.format(
            "PdfFormat{bodySize=%.1f, titleSize=%.1f, leftMargin=%.0f, " +
            "paraWidth=%.0f, lineSpacing=%.1f, tableY=%.0f, pageWidth=%.0f}",
            bodySize, titleSize, leftMargin, paraWidth, lineSpacing, tableY, pageWidth
        );
    }
}

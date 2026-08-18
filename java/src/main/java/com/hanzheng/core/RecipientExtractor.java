package com.hanzheng.core;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * 收函单位自动提取 — 对应 Python replace_header_v4.py 的 extract_recipient()。
 *
 * 从 PDF 首页上半部分文字中提取收函单位（被审计单位）名称。
 * 优先级顺序与 Python 版本完全一致（4 级）。
 */
public class RecipientExtractor {

    // ================================================================
    // 公开接口
    // ================================================================

    /**
     * 从 PDF 首页提取收函单位
     *
     * @param pdfPath PDF 文件路径
     * @return [recipientName, confidence] — confidence: "high" | "medium" | "low"
     */
    public static String[] extract(File pdfPath) throws IOException {
        List<String> textLines = extractTextLines(pdfPath, 50, 300);
        if (textLines.isEmpty()) {
            return new String[]{"", "low"};
        }

        // ---- 策略1：查找 "单位：" ----
        for (String line : textLines) {
            if (line.contains("单位：")) {
                String after = line.split("单位：", 2)[1].trim();
                if (!after.isEmpty()) {
                    return new String[]{after, isLikelyCompanyName(after) ? "high" : "high"};
                }
            }
        }

        // ---- 策略2：查找 "致：" ----
        for (String line : textLines) {
            if (line.contains("致：")) {
                String after = line.split("致：", 2)[1].trim();
                if (!after.isEmpty()) {
                    return new String[]{after, isLikelyCompanyName(after) ? "high" : "high"};
                }
            }
        }

        // ---- 策略3：查找 "敬启者" ----
        for (int i = 0; i < textLines.size(); i++) {
            String line = textLines.get(i);
            if (line.contains("敬启者")) {
                // 3a: 同一行中"敬启者"前后的文本
                String[] parts = line.split("敬启者", 2);
                String before = parts[0].trim();
                String after = parts.length > 1 ? parts[1].replaceFirst("^[：:]", "").trim() : "";
                if (!before.isEmpty() && isLikelyCompanyName(before)) {
                    return new String[]{before, "high"};
                }
                if (!after.isEmpty() && isLikelyCompanyName(after)) {
                    return new String[]{after, "high"};
                }

                // 3b: 前后各 3 行搜索
                for (int offset = -3; offset <= 3; offset++) {
                    if (offset == 0) continue;
                    int idx = i + offset;
                    if (idx >= 0 && idx < textLines.size()) {
                        if (isLikelyCompanyName(textLines.get(idx))) {
                            return new String[]{textLines.get(idx).trim(), "medium"};
                        }
                    }
                }
            }
        }

        // ---- 策略4：兜底 —— 搜索区域内第一个符合公司名特征的行 ----
        for (String line : textLines) {
            if (isLikelyCompanyName(line)) {
                return new String[]{line.trim(), "medium"};
            }
        }

        return new String[]{"", "low"};
    }

    /**
     * 仅提取文本行列表（不执行搜索策略，供外部使用）
     *
     * @return 返回 [文本行列表, 首行原文]
     */
    public static String[] extractLine(File pdfPath) throws IOException {
        List<String> lines = extractTextLines(pdfPath, 50, 300);
        String fullText = String.join("\n", lines);
        String firstLine = lines.isEmpty() ? "" : lines.get(0).trim();
        return new String[]{fullText, firstLine};
    }

    // ================================================================
    // 内部实现
    // ================================================================

    /**
     * 判断文本是否像公司名称。
     * 纯/主要为中文，4~30 字符，且不包含排除关键词。
     */
    private static boolean isLikelyCompanyName(String text) {
        String candidate = text.trim();
        if (candidate.length() < 4 || candidate.length() > 30) {
            return false;
        }
        // 过滤明显不是公司名的关键词
        String[] skipKeywords = {
            "单位：", "致：", "敬启者", "编号：", "编号", "索引",
            "询证函", "会计师", "审计", "列示", "余额",
            "项目联系人", "回函地址", "收件人", "邮编", "邮箱",
            "电话", "传真"
        };
        for (String kw : skipKeywords) {
            if (candidate.contains(kw)) {
                return false;
            }
        }
        // 至少一半是中文字符，且至少 4 个中文
        int chineseCount = 0;
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') {
                chineseCount++;
            }
        }
        return chineseCount >= 4 && chineseCount >= candidate.length() * 0.5;
    }

    /**
     * 从 PDF 首页指定 y 范围内逐行提取文本。
     *
     * 注意：Python 版本用 pdfplumber 按精确坐标分组，Java 用 PDFBox
     * 文本提取。PDFBox 的 PDFTextStripper 输出的文本已经按行排列，
     * 但不是基于精确 y 坐标。这里简化处理，提取首页全部文本行再过滤。
     *
     * 对于格式不规范的 PDF，可能需要用 pdfbox 底层的 TextPosition
     * 来按精确 y 坐标重建文本行，但简单场景下已足够。
     */
    private static List<String> extractTextLines(File pdfPath, float yMin, float yMax) throws IOException {
        List<String> allLines = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(pdfPath)) {
            // 使用 PDFTextStripper 提取首页文本
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            for (String line : text.split("\\r?\\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    allLines.add(trimmed);
                }
            }
        }

        // 如果文本行很少，可能是格式特殊的 PDF，
        // 此时不做 y 坐标过滤，直接返回所有行
        if (allLines.size() <= 10) {
            return allLines;
        }

        // 通常 PDF 首页前 ~1/3 部分包含标题和收函单位信息
        // 取前三分之一的文本行
        int headerCount = Math.max(5, allLines.size() / 3);
        return allLines.subList(0, Math.min(headerCount, allLines.size()));
    }
}

package com.hanzheng.core;

import java.util.ArrayList;
import java.util.List;

/**
 * CJK 文本手动断行工具
 *
 * 原理：预计算每个字符的渲染宽度，在段落宽度内自然换行，
 * 避免 PDF 引擎自动换行时标点符号错位。
 *
 * 字符宽度规则（基于 NSimSun 等宽字体）：
 *   - 全角 CJK 字符：宽度 = fontsize
 *   - 半角 ASCII (< 0x80)：宽度 = fontsize × 0.55
 *   - 其他字符：宽度 = fontsize
 */
public class CjkWrapper {

    /**
     * 判断是否为全角 CJK 字符
     */
    public static boolean isCjkFullwidth(int codePoint) {
        return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)  // CJK 统一表意文字
            || (codePoint >= 0x3000 && codePoint <= 0x303F)   // CJK 标点
            || (codePoint >= 0xFF00 && codePoint <= 0xFFEF)   // 全角字母/符号
            || (codePoint >= 0x2E80 && codePoint <= 0x2FDF)   // CJK 部首
            || (codePoint >= 0x3400 && codePoint <= 0x4DBF)   // CJK 扩展A
            || (codePoint >= 0xF900 && codePoint <= 0xFAFF);  // CJK 兼容
    }

    /**
     * 估算单字符渲染宽度
     */
    public static double charWidth(int codePoint, double fontSize) {
        if (isCjkFullwidth(codePoint)) {
            return fontSize;
        } else if (codePoint < 0x80) {
            return fontSize * 0.55;
        } else {
            return fontSize;
        }
    }

    /**
     * 手动断行：按字符宽度计算，在段落宽度内自然换行。
     *
     * @param text        原始文本
     * @param fontSize    字号 (pt)
     * @param availWidth  可用宽度 (pt)
     * @return 断行后的字符串列表，每项为一个不超宽的文本行
     */
    public static List<String> wrapCjkText(String text, double fontSize, double availWidth) {
        List<String> lines = new ArrayList<String>();

        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }

        StringBuilder curLine = new StringBuilder();
        double curWidth = 0.0;

        int[] codePoints = text.codePoints().toArray();
        for (int cp : codePoints) {
            double cw = charWidth(cp, fontSize);

            if (curWidth + cw > availWidth && curLine.length() > 0) {
                // 当前行已满，开始新行
                lines.add(curLine.toString());
                curLine.setLength(0);
                curLine.appendCodePoint(cp);
                curWidth = cw;
            } else {
                curLine.appendCodePoint(cp);
                curWidth += cw;
            }
        }

        if (curLine.length() > 0) {
            lines.add(curLine.toString());
        }

        return lines.isEmpty() ? java.util.Collections.singletonList("") : lines;
    }
}

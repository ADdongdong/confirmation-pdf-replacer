package com.hanzheng;

import com.hanzheng.core.PdfProcessor;
import com.hanzheng.model.HanzhengRequest;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * 函证头部替换工具 - CLI 主入口
 *
 * 用法：
 *   java -jar hanzheng-pdf-tool.jar <会所PDF> -t <话术.txt> -o <输出.pdf>
 *
 * 话术文件格式：
 *   TITLE:标题文字          → 居中大字号
 *   RIGHT:右对齐文字        → 右上角小字
 *   正文内容...              → 左对齐正文
 *   ---                     → 分隔线（上面是头部，下面是联系方式）
 *   项目联系人：张三         → 联系方式区
 */
public class HanzhengPdfTool {

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("  函证头部替换工具 v1.0 (Java)");
        System.out.println("============================================================");

        // 解析命令行参数
        String inputPath = null;
        String textPath = null;
        String outputPath = null;

        List<String> positional = new ArrayList<String>();
        for (int i = 0; i < args.length; i++) {
            if ("-t".equals(args[i]) || "--text".equals(args[i])) {
                textPath = args[++i];
            } else if ("-o".equals(args[i]) || "--output".equals(args[i])) {
                outputPath = args[++i];
            } else if (!args[i].startsWith("-")) {
                positional.add(args[i]);
            }
        }

        if (!positional.isEmpty()) inputPath = positional.get(0);

        if (inputPath == null || textPath == null) {
            System.err.println("用法: java -jar hanzheng-pdf-tool.jar <会所PDF> -t <话术.txt> [-o <输出.pdf>]");
            System.exit(1);
        }

        if (!new File(inputPath).exists()) {
            System.err.println("✗ 输入文件不存在: " + inputPath);
            System.exit(1);
        }
        if (!new File(textPath).exists()) {
            System.err.println("✗ 话术文件不存在: " + textPath);
            System.exit(1);
        }

        if (outputPath == null) {
            String base = inputPath.replaceAll("\\.[^.]+$", "");
            outputPath = base + "_fixed.pdf";
        }

        try {
            // Step 1: 解析话术
            System.out.println("\n[1] 解析话术...");
            ScriptConfig config = parseConfig(textPath);
            System.out.printf("  话术: 头部 %d 行, 联系方式 %d 行%n",
                    config.headerLines.size(), config.contactLines.size());

            // Step 2: 构建请求（从话术文本拼接）
            HanzhengRequest req = buildRequestFromScript(config, inputPath, outputPath);

            // Step 3: 处理
            System.out.println("\n[2] 处理 PDF...");
            PdfProcessor processor = new PdfProcessor();
            String resultPath = processor.process(req);

            System.out.println();
            System.out.println("✓ 输出: " + resultPath);
            System.out.println("============================================================");

        } catch (Exception e) {
            System.err.println("✗ 处理失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // ================================================================
    // 话术解析（兼容原 Python 版本格式）
    // ================================================================

    private static class ScriptConfig {
        final List<String[]> headerLines = new ArrayList<String[]>();  // [type, text]
        final List<String> contactLines = new ArrayList<String>();
    }

    private static ScriptConfig parseConfig(String textPath) throws IOException {
        ScriptConfig config = new ScriptConfig();
        boolean inContact = false;

        List<String> lines = Files.readAllLines(Paths.get(textPath), StandardCharsets.UTF_8);
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if ("---".equals(line)) {
                inContact = true;
                continue;
            }

            if (inContact) {
                config.contactLines.add(line);
            } else if (line.startsWith("TITLE:") || line.startsWith("title:")) {
                config.headerLines.add(new String[]{"title", line.substring(6)});
            } else if (line.startsWith("RIGHT:") || line.startsWith("right:")) {
                config.headerLines.add(new String[]{"right", line.substring(6)});
            } else {
                config.headerLines.add(new String[]{"body", line});
            }
        }

        return config;
    }

    /**
     * 从话术构建 HanzhengRequest。
     * 注意：CLI 模式下从话术文本直接拼接，与 Web 表单模式不同。
     * 这里为简化，将头部标题+正文拼接为一个 bodyText，联系方式逐行放入。
     *
     * 正式使用时推荐 Web 表单模式（WebServer），CLI 模式主要用于兼容旧话术文件。
     */
    private static HanzhengRequest buildRequestFromScript(ScriptConfig config,
                                                           String inputPath,
                                                           String outputPath) {
        HanzhengRequest req = new HanzhengRequest();
        req.inputPath = inputPath;
        req.outputPath = outputPath;

        // 从话术头部解析：分离 "致：XXX" 和正文
        String extractedCompany = "";
        StringBuilder body = new StringBuilder();
        for (String[] hl : config.headerLines) {
            if ("title".equals(hl[0])) {
                // 标题已经固定为"企业询证函"，跳过
                continue;
            }
            if ("right".equals(hl[0])) {
                // 右对齐行（函证编码/索引号）暂不单独渲染，跳过
                continue;
            }
            String text = hl[1].trim();
            if (text.startsWith("致：") || text.startsWith("致:")) {
                // 提取致函单位
                extractedCompany = text.substring(2).trim();
            } else {
                // 正文段落
                body.append(text).append("\n");
            }
        }
        req.companyName = extractedCompany.isEmpty() ? "（请填写致函单位）" : extractedCompany;
        req.bodyText = body.toString().trim();
        if (req.bodyText.isEmpty()) {
            req.bodyText = "（从话术文件读取，请检查话术格式）";
        }

        // 解析联系方式
        Map<String, String> contactMap = new LinkedHashMap<String, String>();
        for (String cl : config.contactLines) {
            int colonIdx = cl.indexOf('：');
            if (colonIdx < 0) colonIdx = cl.indexOf(':');
            if (colonIdx > 0) {
                String key = cl.substring(0, colonIdx).trim();
                String val = cl.substring(colonIdx + 1).trim();
                contactMap.put(key, val);
            }
        }

        req.contactPerson = contactMap.getOrDefault("项目联系人", "");
        req.contactPhone = contactMap.getOrDefault("项目联系人电话", "");
        req.recipient = contactMap.getOrDefault("收件人", "");
        req.recipientPhone = contactMap.getOrDefault("收件人电话", "");
        req.email = contactMap.getOrDefault("邮箱", "");

        // 回函地址也可以作为联系人补充信息
        String replyAddr = contactMap.get("回函地址");
        if (replyAddr != null && !replyAddr.isEmpty()) {
            // 如果收件人为空，用回函地址的一部分
            if (req.recipient.isEmpty()) {
                req.recipient = replyAddr.length() > 30 ? replyAddr.substring(0, 27) + "..." : replyAddr;
            }
        }

        return req;
    }
}

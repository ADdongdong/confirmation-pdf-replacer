package com.hanzheng.model;

import java.util.*;

/**
 * 批量处理作业模型 — 对应 Python web_form_server.py 中的 _batch_sessions 字典。
 *
 * 每个作业由一个 token 标识，包含一组上传的 PDF 及其提取的收函单位。
 */
public class BatchJob {

    public final String token;
    public final long createdAt;

    /** 上传的文件列表（Python 契约：id → FileEntry） */
    public final LinkedHashMap<String, FileEntry> files = new LinkedHashMap<>();

    /** 生成结果列表 */
    public final LinkedHashMap<String, ResultEntry> results = new LinkedHashMap<>();

    public String bodyText = "";
    public Map<String, String> contacts = new LinkedHashMap<>();
    public Double whiteoutBottom;   // null=自动检测
    public Double footerHeight;     // null=默认22

    // ZIP 结果
    public String zipPath;
    public String zipName;
    public String status = "uploaded";

    public BatchJob(String token) {
        this.token = token;
        this.createdAt = System.currentTimeMillis();
    }

    // ---- 文件条目 ----
    public static class FileEntry {
        /** 文件 id（Python 契约：uuid hex） */
        public String id;
        public String name;
        public String recipient;
        public String confidence;   // "high" | "low"
        public String previewUrl;   // 缩略图 URL 或 null

        public FileEntry(String id, String name) {
            this.id = id;
            this.name = name;
            this.recipient = "";
            this.confidence = "low";
            this.previewUrl = null;
        }
    }

    // ---- 生成结果 ----
    public static class ResultEntry {
        public String name;      // 原始文件名
        public String file;      // 输出文件名（ZIP 内条目名）
        public String recipient;
        public String error;

        public ResultEntry(String name, String file, String recipient) {
            this.name = name;
            this.file = file;
            this.recipient = recipient;
            this.error = "";
        }
    }
}

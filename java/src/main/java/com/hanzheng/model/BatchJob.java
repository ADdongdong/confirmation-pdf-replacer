package com.hanzheng.model;

import java.util.*;

/**
 * 批量处理作业模型 — 对应 Python web_form_server.py 中的 batch_jobs 字典。
 *
 * 每个作业由一个 token 标识，包含一组上传的 PDF 及其提取的收函单位。
 */
public class BatchJob {

    public final String token;
    public final long createdAt;

    /** 上传的文件列表，key = 文件名 */
    public final LinkedHashMap<String, FileEntry> files = new LinkedHashMap<>();

    /** 生成结果列表，key = 输出文件名 */
    public final LinkedHashMap<String, ResultEntry> results = new LinkedHashMap<>();

    public String companyName  = "";
    public String bodyText      = "";
    public String contactPerson = "";
    public String contactPhone  = "";
    public String recipient     = "";
    public String recipientPhone = "";
    public String email         = "";
    public double whiteoutBottom = 280; // 默认值

    public BatchJob(String token) {
        this.token = token;
        this.createdAt = System.currentTimeMillis();
    }

    // ---- 文件条目 ----
    public static class FileEntry {
        /** 服务器上的 pdf_content/ 中的 key（不含扩展名） */
        public String key;
        public String filename;
        public String recipient;   // 提取的收函单位
        public String extractedLine; // 提取的原始行文本
        public long size;           // 文件大小（字节）

        public FileEntry(String key, String filename, long size) {
            this.key = key;
            this.filename = filename;
            this.size = size;
            this.recipient = "";
            this.extractedLine = "";
        }
    }

    // ---- 生成结果 ----
    public static class ResultEntry {
        public String filename;  // 原始文件名
        public String status;    // "success" | "error"
        public String outputFile; // 输出文件名（用于下载）
        public String error;

        public ResultEntry(String filename, String status) {
            this.filename = filename;
            this.status = status;
            this.outputFile = "";
            this.error = "";
        }
    }
}

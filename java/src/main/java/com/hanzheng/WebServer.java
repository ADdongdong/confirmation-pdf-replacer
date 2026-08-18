package com.hanzheng;

import com.hanzheng.core.*;
import com.hanzheng.model.*;
import com.sun.net.httpserver.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/**
 * HTTP Web 服务器 — 投行函证 PDF 头部替换工具
 *
 * 路由一览（与 Python web_form_server.py 对齐）：
 *   GET  /                         单文件处理页面
 *   GET  /upload                   批量处理页面（主入口）
 *   GET  /config                   配置管理页面（重定向到 /upload）
 *   POST /generate                 单文件处理
 *   GET  /preview                  预览原 PDF
 *   GET  /download/<filename>      下载单文件结果
 *
 *   批量处理：
 *   POST /batch/upload             上传多个 PDF，自动提取收函单位
 *   POST /batch/generate           批量生成
 *   POST /batch/re-recognize       重新识别单个文件收函单位
 *   GET  /batch/download           下载生成结果
 *
 *   配置管理：
 *   GET  /config/list              列出所有配置
 *   GET  /config/load/<name>       加载配置
 *   POST /config/save              保存配置
 *   POST /config/delete/<name>     删除配置
 *   GET  /config/default           获取默认配置
 */
public class WebServer {

    private static final String VERSION = "4.0";
    private static final int PORT = 8889;

    // ---- 静态资源 ----
    private static String cachedIndexHtml = null;
    private static String cachedBatchUploadHtml = null;

    // ---- 批量作业存储 ----
    private static final ConcurrentHashMap<String, BatchJob> batchJobs = new ConcurrentHashMap<>();

    // ---- PDF 内容缓存（upload 后存储的原文件） ----
    private static final ConcurrentHashMap<String, byte[]> pdfContentCache = new ConcurrentHashMap<>();

    // ---- 生成结果存储 ----
    private static final ConcurrentHashMap<String, byte[]> resultCache = new ConcurrentHashMap<>();


    public static void main(String[] args) throws IOException {
        // 确保 configs 目录存在
        ConfigManager.ensureDir();

        // 预加载 HTML
        cachedIndexHtml = loadResource("index.html");
        cachedBatchUploadHtml = loadResource("batch_upload.html");

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // ---- 页面 ----
        server.createContext("/", new PageHandler());
        server.createContext("/upload", new PageHandler());

        // ---- 单文件处理 ----
        server.createContext("/generate", new GenerateHandler());
        server.createContext("/preview", new PreviewHandler());
        server.createContext("/download", new DownloadHandler()); // 含子路径

        // ---- 批量处理 ----
        server.createContext("/batch", new BatchHandler());

        // ---- 配置管理 ----
        server.createContext("/config", new ConfigHandler());

        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("=================================================");
        System.out.println("  投行函证 PDF 头部替换工具 v" + VERSION);
        System.out.println("  服务已启动: http://localhost:" + PORT);
        System.out.println("  批量处理:   http://localhost:" + PORT + "/upload");
        System.out.println("=================================================");
    }

    // ================================================================
    // 工具方法
    // ================================================================

    /** 读取 classpath 资源文件 */
    private static String loadResource(String name) {
        try (InputStream is = WebServer.class.getClassLoader().getResourceAsStream(name)) {
            if (is == null) return "";
            java.util.Scanner s = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
            return s.hasNext() ? s.next() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** 设置 CORS 头 */
    private static void setCors(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    /** 发送 JSON 响应 */
    private static void sendJson(HttpExchange exchange, String json) throws IOException {
        setCors(exchange);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 发送 JSON 错误 */
    private static void sendJsonError(HttpExchange exchange, int code, String error) throws IOException {
        setCors(exchange);
        String json = "{\"success\":false,\"error\":\"" + escapeJson(error) + "\"}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 发送 HTML 响应 */
    private static void sendHtml(HttpExchange exchange, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 返回纯文本 */
    private static void sendText(HttpExchange exchange, int code, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 解析 URL 编码的表单数据 */
    private static Map<String, String> parseForm(byte[] body) {
        Map<String, String> params = new LinkedHashMap<>();
        if (body == null || body.length == 0) return params;
        try {
            String s = new String(body, StandardCharsets.UTF_8);
            for (String pair : s.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    params.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"),
                               URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
                }
            }
        } catch (Exception ignored) {}
        return params;
    }

    /** 解析 multipart/form-data（简单实现） */
    private static Map<String, MultipartEntry> parseMultipart(String contentType, byte[] body) {
        Map<String, MultipartEntry> result = new LinkedHashMap<>();
        if (body == null || body.length == 0) return result;
        try {
            String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
            boundary = boundary.replace("\"", "");
            byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.UTF_8);

            // 查找所有 boundary 位置
            int pos = 0;
            while (pos < body.length) {
                int start = indexOf(body, boundaryBytes, pos);
                if (start < 0) break;
                int headerEnd = indexOf(body, "\r\n\r\n".getBytes(), start);
                if (headerEnd < 0) break;
                int contentStart = headerEnd + 4;

                // 找到下一个 boundary
                int nextBoundary = indexOf(body, boundaryBytes, contentStart);
                if (nextBoundary < 0) {
                    // 也可能是 "--boundary--"
                    byte[] endBoundary = ("--" + boundary + "--").getBytes(StandardCharsets.UTF_8);
                    int endPos = indexOf(body, endBoundary, contentStart);
                    if (endPos < 0) break;
                    nextBoundary = endPos;
                }

                int contentEnd = nextBoundary - 2; // 去掉 \r\n
                if (contentEnd > contentStart) {
                    String headerStr = new String(body, start, headerEnd - start, StandardCharsets.UTF_8);
                    // 提取 name
                    String name = "";
                    int nameIdx = headerStr.indexOf("name=\"");
                    if (nameIdx >= 0) {
                        int nameEnd = headerStr.indexOf("\"", nameIdx + 6);
                        if (nameEnd > 0) {
                            name = headerStr.substring(nameIdx + 6, nameEnd);
                        }
                    }
                    // 提取 filename
                    String filename = "";
                    int fnIdx = headerStr.indexOf("filename=\"");
                    if (fnIdx >= 0) {
                        int fnEnd = headerStr.indexOf("\"", fnIdx + 10);
                        if (fnEnd > 0) {
                            filename = headerStr.substring(fnIdx + 10, fnEnd);
                        }
                    }

                    MultipartEntry entry = new MultipartEntry();
                    entry.name = name;
                    entry.filename = filename;
                    entry.data = Arrays.copyOfRange(body, contentStart, contentEnd);
                    result.put(name, entry);
                }

                pos = nextBoundary + boundaryBytes.length;
                // 跳过末尾 "--"
                if (pos < body.length && body[pos] == '-' && pos + 1 < body.length && body[pos + 1] == '-') {
                    break;
                }
                if (pos < body.length && body[pos] == '\r') pos += 2;
            }
        } catch (Exception e) {
            System.err.println("[parseMultipart] Error: " + e.getMessage());
        }
        return result;
    }

    private static int indexOf(byte[] data, byte[] needle, int from) {
        outer:
        for (int i = from; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    /** 从请求 URI 中提取路径（去掉 query string） */
    private static String getPath(HttpExchange exchange) {
        return exchange.getRequestURI().getPath();
    }

    /** 读取请求体 */
    private static byte[] readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        }
    }

    /** 生成唯一 token */
    private static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** 清理过期作业（1 小时） */
    private static void cleanupExpired() {
        long now = System.currentTimeMillis();
        batchJobs.entrySet().removeIf(e -> now - e.getValue().createdAt > 3600000L);
    }

    // ================================================================
    // 页面处理器
    // ================================================================
    static class PageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = getPath(exchange);
            String method = exchange.getRequestMethod();

            if ("OPTIONS".equals(method)) {
                setCors(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("/upload".equals(path)) {
                sendHtml(exchange, cachedBatchUploadHtml);
            } else {
                sendHtml(exchange, cachedIndexHtml);
            }
        }
    }

    // ================================================================
    // 单文件生成
    // ================================================================
    static class GenerateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                setCors(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            byte[] body = readBody(exchange);

            if (contentType != null && contentType.contains("multipart/form-data")) {
                handleMultipart(exchange, body);
            } else {
                handleForm(exchange, body);
            }
        }

        private void handleMultipart(HttpExchange exchange, byte[] body) throws IOException {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            Map<String, MultipartEntry> parts = parseMultipart(contentType, body);

            MultipartEntry pdfEntry = parts.get("pdf");
            if (pdfEntry == null || pdfEntry.data.length == 0) {
                sendJsonError(exchange, 400, "未找到 PDF 文件");
                return;
            }

            HanzhengRequest req = new HanzhengRequest();
            req.companyName = getPartValue(parts, "company_name");
            req.bodyText = getPartValue(parts, "body_text");
            req.contactPerson = getPartValue(parts, "contact_person");
            req.contactPhone = getPartValue(parts, "contact_phone");
            req.recipient = getPartValue(parts, "recipient");
            req.recipientPhone = getPartValue(parts, "recipient_phone");
            req.email = getPartValue(parts, "email");
            try { req.whiteoutBottom = Double.parseDouble(getPartValue(parts, "whiteout_bottom", "280")); } catch (Exception e) { req.whiteoutBottom = 280.0; }

            processAndRespond(exchange, pdfEntry.data, pdfEntry.filename, req);
        }

        private void handleForm(HttpExchange exchange, byte[] body) throws IOException {
            Map<String, String> params = parseForm(body);
            // 表单模式的生成暂不支持（保持向后兼容占位）
            sendJsonError(exchange, 400, "请使用 multipart/form-data 上传 PDF 文件");
        }

        private String getPartValue(Map<String, MultipartEntry> parts, String key) {
            return getPartValue(parts, key, "");
        }

        private String getPartValue(Map<String, MultipartEntry> parts, String key, String defaultValue) {
            MultipartEntry e = parts.get(key);
            if (e == null || e.data == null || e.data.length == 0) return defaultValue;
            return new String(e.data, StandardCharsets.UTF_8).trim();
        }

        private void processAndRespond(HttpExchange exchange, byte[] pdfData, String filename,
                                        HanzhengRequest req) throws IOException {
            File tempDir = new File("temp");
            if (!tempDir.exists()) tempDir.mkdirs();
            String baseName = System.currentTimeMillis() + "_" + (filename != null ? filename : "input.pdf");
            File inputFile = new File(tempDir, baseName);
            String outputKey = generateToken();
            File outputFile = new File(tempDir, "out_" + outputKey + ".pdf");

            try {
                Files.write(inputFile.toPath(), pdfData);

                // 设置输入输出路径
                req.inputPath = inputFile.getAbsolutePath();
                req.outputPath = outputFile.getAbsolutePath();

                // 调用 PdfProcessor（实例方法，返回输出路径）
                new PdfProcessor().process(req);

                // 读取输出文件用于缓存
                byte[] result = Files.readAllBytes(outputFile.toPath());
                resultCache.put(outputKey, result);

                String json = "{\"success\":true,\"download\":\"/download/" + outputKey + "\","
                        + "\"filename\":\"" + escapeJson(filename) + "\","
                        + "\"recipient\":\"" + escapeJson(req.recipient) + "\"}";
                sendJson(exchange, json);
            } catch (Exception e) {
                sendJsonError(exchange, 500, "处理失败: " + e.getMessage());
                e.printStackTrace();
            } finally {
                inputFile.delete();
                outputFile.delete();
            }
        }
    }

    // ================================================================
    // 预览
    // ================================================================
    static class PreviewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCors(exchange);
            String fileName = getPath(exchange).replace("/preview/", "");
            File pdfFile = new File("data/" + fileName);
            if (!pdfFile.exists()) {
                sendText(exchange, 404, "File not found");
                return;
            }
            byte[] content = Files.readAllBytes(pdfFile.toPath());
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        }
    }

    // ================================================================
    // 下载
    // ================================================================
    static class DownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCors(exchange);
            String path = getPath(exchange);
            String key = path.replace("/download/", "").replace("/download", "");

            if (key.isEmpty()) {
                sendText(exchange, 400, "缺少文件标识");
                return;
            }

            byte[] data = resultCache.get(key);
            if (data != null) {
                exchange.getResponseHeaders().set("Content-Type", "application/pdf");
                exchange.getResponseHeaders().set("Content-Disposition",
                        "attachment; filename=\"processed_" + key + ".pdf\"");
                exchange.sendResponseHeaders(200, data.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(data);
                }
                // 交付后清理
                resultCache.remove(key);
                return;
            }

            sendText(exchange, 404, "文件不存在或已过期");
        }
    }

    // ================================================================
    // 批量处理处理器
    // ================================================================
    static class BatchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = getPath(exchange);

            if ("OPTIONS".equals(method)) {
                setCors(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            switch (path) {
                case "/batch/upload":
                    if ("POST".equals(method)) handleBatchUpload(exchange);
                    else sendJsonError(exchange, 405, "Method not allowed");
                    break;
                case "/batch/generate":
                    if ("POST".equals(method)) handleBatchGenerate(exchange);
                    else sendJsonError(exchange, 405, "Method not allowed");
                    break;
                case "/batch/re-recognize":
                    if ("POST".equals(method)) handleReRecognize(exchange);
                    else sendJsonError(exchange, 405, "Method not allowed");
                    break;
                case "/batch/download":
                    if ("GET".equals(method)) handleBatchDownload(exchange);
                    else sendJsonError(exchange, 405, "Method not allowed");
                    break;
                default:
                    sendJsonError(exchange, 404, "Not found");
            }
        }

        /**
         * POST /batch/upload
         * 接收多个 PDF 文件，自动提取收函单位
         */
        private void handleBatchUpload(HttpExchange exchange) throws IOException {
            cleanupExpired();

            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            byte[] body = readBody(exchange);

            if (contentType == null || !contentType.contains("multipart/form-data")) {
                sendJsonError(exchange, 400, "请使用 multipart/form-data 上传文件");
                return;
            }

            Map<String, MultipartEntry> parts = parseMultipart(contentType, body);

            if (parts.isEmpty()) {
                sendJsonError(exchange, 400, "未收到文件");
                return;
            }

            String token = generateToken();
            BatchJob job = new BatchJob(token);

            // 创建临时目录存储上传的 PDF
            File pdfDir = new File("temp/pdf_content");
            if (!pdfDir.exists()) pdfDir.mkdirs();

            StringBuilder filesJson = new StringBuilder();
            boolean first = true;

            for (Map.Entry<String, MultipartEntry> entry : parts.entrySet()) {
                MultipartEntry part = entry.getValue();
                if (part.data == null || part.data.length == 0) continue;
                if (part.filename == null || part.filename.isEmpty()) continue;

                String key = token + "_" + (job.files.size() + 1);
                File pdfFile = new File(pdfDir, key + ".pdf");
                Files.write(pdfFile.toPath(), part.data);

                BatchJob.FileEntry fe = new BatchJob.FileEntry(key, part.filename, part.data.length);
                job.files.put(part.filename, fe);

                // 缓存原文件内容
                pdfContentCache.put("content:" + key, part.data);

                // 自动提取收函单位
                try {
                    String[] result = RecipientExtractor.extract(pdfFile);
                    fe.recipient = result[0];
                    fe.extractedLine = result[0];
                } catch (Exception e) {
                    fe.recipient = "";
                    fe.extractedLine = "";
                }

                if (!first) filesJson.append(",");
                first = false;
                filesJson.append("{")
                    .append("\"key\":\"").append(key).append("\",")
                    .append("\"filename\":\"").append(escapeJson(part.filename)).append("\",")
                    .append("\"recipient\":\"").append(escapeJson(fe.recipient)).append("\",")
                    .append("\"extractedLine\":\"").append(escapeJson(fe.extractedLine)).append("\",")
                    .append("\"size\":").append(part.data.length).append(",")
                    .append("\"confidence\":\"").append(fe.recipient.isEmpty() ? "low" : "medium").append("\"")
                    .append("}");
            }

            batchJobs.put(token, job);

            String json = "{\"success\":true,\"token\":\"" + token + "\","
                    + "\"count\":" + job.files.size() + ","
                    + "\"files\":[" + filesJson + "]}";
            sendJson(exchange, json);
        }

        /**
         * POST /batch/generate
         * 接收配置参数 + token，批量处理所有已上传文件
         */
        private void handleBatchGenerate(HttpExchange exchange) throws IOException {
            byte[] body = readBody(exchange);
            String jsonStr = new String(body, StandardCharsets.UTF_8);

            // 简单 JSON 解析
            String token = extractJsonString(jsonStr, "token");
            if (token.isEmpty()) {
                sendJsonError(exchange, 400, "缺少 token");
                return;
            }

            BatchJob job = batchJobs.get(token);
            if (job == null) {
                sendJsonError(exchange, 404, "会话已过期，请重新上传");
                return;
            }

            // 构建配置
            HanzhengRequest req = new HanzhengRequest();
            req.companyName = extractJsonString(jsonStr, "company_name");
            req.bodyText = extractJsonString(jsonStr, "body_text");
            req.contactPerson = extractJsonString(jsonStr, "contact_person");
            req.contactPhone = extractJsonString(jsonStr, "contact_phone");
            req.recipient = extractJsonString(jsonStr, "recipient");
            req.recipientPhone = extractJsonString(jsonStr, "recipient_phone");
            req.email = extractJsonString(jsonStr, "email");
            try {
                req.whiteoutBottom = Double.parseDouble(extractJsonString(jsonStr, "whiteout_bottom", "280"));
            } catch (Exception e) {
                req.whiteoutBottom = 280.0;
            }

            // 更新 job 配置
            job.companyName = req.companyName;
            job.bodyText = req.bodyText;
            job.contactPerson = req.contactPerson;
            job.contactPhone = req.contactPhone;
            job.recipient = req.recipient;
            job.recipientPhone = req.recipientPhone;
            job.email = req.email;
            job.whiteoutBottom = (req.whiteoutBottom != null) ? req.whiteoutBottom : 280;

            File tempDir = new File("temp/pdf_content");
            if (!tempDir.exists()) tempDir.mkdirs();
            StringBuilder resultsJson = new StringBuilder();
            boolean first = true;

            for (Map.Entry<String, BatchJob.FileEntry> entry : job.files.entrySet()) {
                BatchJob.FileEntry fe = entry.getValue();
                String key = fe.key;

                if (!first) resultsJson.append(",");
                first = false;

                try {
                    // 使用缓存的原始 PDF
                    byte[] pdfData = pdfContentCache.get("content:" + key);
                    if (pdfData == null) {
                        File pdfFile = new File(tempDir, key + ".pdf");
                        if (pdfFile.exists()) {
                            pdfData = Files.readAllBytes(pdfFile.toPath());
                        } else {
                            throw new Exception("原文件不存在");
                        }
                    }

                    // 准备输入文件
                    File inputFile = new File(tempDir, "gen_" + key + ".pdf");
                    Files.write(inputFile.toPath(), pdfData);

                    // 为每个文件构建独立的 HanzhengRequest
                    HanzhengRequest fileReq = new HanzhengRequest();
                    fileReq.companyName = req.companyName;
                    fileReq.bodyText = req.bodyText;
                    fileReq.contactPerson = req.contactPerson;
                    fileReq.contactPhone = req.contactPhone;
                    fileReq.recipient = (fe.recipient != null && !fe.recipient.isEmpty()) ? fe.recipient : req.recipient;
                    fileReq.recipientPhone = req.recipientPhone;
                    fileReq.email = req.email;
                    fileReq.whiteoutBottom = req.whiteoutBottom;
                    fileReq.inputPath = inputFile.getAbsolutePath();

                    // 输出文件
                    String outputKey = token + "_out_" + key;
                    File outputFile = new File(tempDir, "out_" + outputKey + ".pdf");
                    fileReq.outputPath = outputFile.getAbsolutePath();

                    // 处理（PdfProcessor 返回输出路径）
                    new PdfProcessor().process(fileReq);

                    // 读取输出文件用于缓存
                    byte[] result = Files.readAllBytes(outputFile.toPath());
                    resultCache.put(outputKey, result);

                    String outputFilename = fe.filename.replace(".pdf", "_已处理.pdf");
                    BatchJob.ResultEntry re = new BatchJob.ResultEntry(fe.filename, "success");
                    re.outputFile = outputKey;
                    job.results.put(outputFilename, re);

                    // 清理临时文件
                    inputFile.delete();
                    outputFile.delete();

                    resultsJson.append("\"").append(escapeJson(outputFilename)).append("\":{")
                        .append("\"filename\":\"").append(escapeJson(fe.filename)).append("\",")
                        .append("\"status\":\"success\",")
                        .append("\"outputKey\":\"").append(outputKey).append("\"")
                        .append("}");

                } catch (Exception e) {
                    BatchJob.ResultEntry re = new BatchJob.ResultEntry(fe.filename, "error");
                    re.error = e.getMessage();
                    job.results.put(fe.filename, re);

                    resultsJson.append("\"").append(escapeJson(fe.filename)).append("\":{")
                        .append("\"filename\":\"").append(escapeJson(fe.filename)).append("\",")
                        .append("\"status\":\"error\",")
                        .append("\"error\":\"").append(escapeJson(e.getMessage())).append("\"")
                        .append("}");
                }
            }

            String response = "{\"success\":true,\"token\":\"" + token + "\","
                    + "\"count\":" + job.results.size() + ","
                    + "\"results\":{" + resultsJson + "}}";
            sendJson(exchange, response);
        }

        /**
         * POST /batch/re-recognize
         * 重新识别单个文件的收函单位
         */
        private void handleReRecognize(HttpExchange exchange) throws IOException {
            byte[] body = readBody(exchange);
            String jsonStr = new String(body, StandardCharsets.UTF_8);

            String token = extractJsonString(jsonStr, "token");
            int index = Integer.parseInt(extractJsonString(jsonStr, "index", "0"));

            BatchJob job = batchJobs.get(token);
            if (job == null) {
                sendJsonError(exchange, 404, "会话已过期");
                return;
            }

            // 找到指定索引的文件
            List<BatchJob.FileEntry> entries = new ArrayList<>(job.files.values());
            if (index < 0 || index >= entries.size()) {
                sendJsonError(exchange, 400, "索引无效");
                return;
            }

            BatchJob.FileEntry fe = entries.get(index);
            File pdfFile = new File("temp/pdf_content/" + fe.key + ".pdf");

            try {
                String[] result = RecipientExtractor.extract(pdfFile);
                fe.recipient = result[0];
                fe.extractedLine = result[0];

                String response = "{\"success\":true,"
                        + "\"recipient\":\"" + escapeJson(result[0]) + "\","
                        + "\"confidence\":\"" + result[1] + "\"}";
                sendJson(exchange, response);
            } catch (Exception e) {
                sendJsonError(exchange, 500, "识别失败: " + e.getMessage());
            }
        }

        /**
         * GET /batch/download?token=xxx&file=outKey  或  &all=1
         */
        private void handleBatchDownload(HttpExchange exchange) throws IOException {
            Map<String, String> params = new LinkedHashMap<>();
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                for (String pair : query.split("&")) {
                    int eq = pair.indexOf('=');
                    if (eq > 0) {
                        params.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"),
                                   URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
                    }
                }
            }

            String token = params.get("token");
            String fileKey = params.get("file");
            String all = params.get("all");

            if (token == null || token.isEmpty()) {
                sendText(exchange, 400, "缺少 token");
                return;
            }

            // 下载全部 → ZIP
            if ("1".equals(all)) {
                downloadAllAsZip(exchange, token);
                return;
            }

            // 下载单个文件
            if (fileKey != null && !fileKey.isEmpty()) {
                byte[] data = resultCache.get(fileKey);
                if (data != null) {
                    exchange.getResponseHeaders().set("Content-Type", "application/pdf");
                    exchange.getResponseHeaders().set("Content-Disposition",
                            "attachment; filename=\"" + URLEncoder.encode(fileKey, "UTF-8") + ".pdf\"");
                    exchange.sendResponseHeaders(200, data.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(data);
                    }
                } else {
                    sendText(exchange, 404, "文件不存在或已过期");
                }
                return;
            }

            sendText(exchange, 400, "请指定 file= 或 all=1");
        }

        private void downloadAllAsZip(HttpExchange exchange, String token) throws IOException {
            BatchJob job = batchJobs.get(token);
            if (job == null) {
                sendText(exchange, 404, "会话已过期");
                return;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                for (Map.Entry<String, BatchJob.ResultEntry> entry : job.results.entrySet()) {
                    BatchJob.ResultEntry re = entry.getValue();
                    if (!"success".equals(re.status)) continue;
                    byte[] data = resultCache.get(re.outputFile);
                    if (data == null) continue;
                    ZipEntry ze = new ZipEntry(entry.getKey());
                    zos.putNextEntry(ze);
                    zos.write(data);
                    zos.closeEntry();
                }
            }

            byte[] zipData = baos.toByteArray();
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"batch_results_" + token + ".zip\"");
            exchange.sendResponseHeaders(200, zipData.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(zipData);
            }
        }

        // ---- 简单 JSON 字符串提取 ----
        private String extractJsonString(String json, String key) {
            return extractJsonString(json, key, "");
        }
        private String extractJsonString(String json, String key, String def) {
            String pattern = "\"" + key + "\":\"";
            int idx = json.indexOf(pattern);
            if (idx < 0) {
                // 尝试数字值
                pattern = "\"" + key + "\":";
                idx = json.indexOf(pattern);
                if (idx < 0) return def;
                int start = idx + pattern.length();
                int end = json.indexOf(',', start);
                if (end < 0) end = json.indexOf('}', start);
                if (end < 0) return def;
                return json.substring(start, end).trim();
            }
            int start = idx + pattern.length();
            int end = json.indexOf('"', start);
            if (end < 0) return def;
            return json.substring(start, end);
        }
    }

    // ================================================================
    // 配置管理处理器
    // ================================================================
    static class ConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = getPath(exchange);

            if ("OPTIONS".equals(method)) {
                setCors(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // GET /config/default
            if ("GET".equals(method) && "/config/default".equals(path)) {
                handleGetDefault(exchange);
                return;
            }

            // GET /config/list
            if ("GET".equals(method) && "/config/list".equals(path)) {
                handleList(exchange);
                return;
            }

            // GET /config/load/<name>
            if ("GET".equals(method) && path.startsWith("/config/load/")) {
                String name = path.substring("/config/load/".length());
                handleLoad(exchange, name);
                return;
            }

            // POST /config/save
            if ("POST".equals(method) && "/config/save".equals(path)) {
                handleSave(exchange);
                return;
            }

            // POST /config/delete/<name>
            if ("POST".equals(method) && path.startsWith("/config/delete/")) {
                String name = path.substring("/config/delete/".length());
                handleDelete(exchange, name);
                return;
            }

            // 默认：重定向到 /upload
            exchange.getResponseHeaders().set("Location", "/upload");
            exchange.sendResponseHeaders(302, -1);
        }

        private void handleGetDefault(HttpExchange exchange) throws IOException {
            Map<String, String> cfg = ConfigManager.loadDefaultConfig();
            String configName = cfg.remove("config_name");
            if (configName == null) configName = "";
            String json = "{\"success\":true,\"config_name\":\"" + escapeJson(configName) + "\","
                    + "\"config\":" + fieldsToJson(cfg) + "}";
            sendJson(exchange, json);
        }

        private void handleList(HttpExchange exchange) throws IOException {
            List<String> configs = ConfigManager.listConfigs();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < configs.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(configs.get(i))).append("\"");
            }
            String json = "{\"success\":true,\"configs\":[" + sb + "],\"default\":\""
                    + escapeJson(ConfigManager.getDefaultConfigName()) + "\"}";
            sendJson(exchange, json);
        }

        private void handleLoad(HttpExchange exchange, String name) throws IOException {
            if (name.isEmpty()) {
                sendJsonError(exchange, 400, "缺少配置名称");
                return;
            }
            Map<String, String> cfg = ConfigManager.loadConfig(name);
            if (cfg == null) {
                sendJsonError(exchange, 404, "配置不存在");
                return;
            }
            String json = "{\"success\":true,\"config_name\":\"" + escapeJson(name) + "\","
                    + "\"config\":" + fieldsToJson(cfg) + "}";
            sendJson(exchange, json);
        }

        private void handleSave(HttpExchange exchange) throws IOException {
            byte[] body = readBody(exchange);
            String jsonStr = new String(body, StandardCharsets.UTF_8);

            // 提取 name
            String name = extractJsonString(jsonStr, "name");
            if (name.isEmpty()) {
                sendJsonError(exchange, 400, "缺少配置名称");
                return;
            }

            // 从 "fields" 子对象中提取字段
            String fieldsBlock = "";
            int fieldsIdx = jsonStr.indexOf("\"fields\"");
            if (fieldsIdx >= 0) {
                int brace = jsonStr.indexOf('{', fieldsIdx);
                if (brace >= 0) {
                    int depth = 0, end = brace;
                    for (int i = brace; i < jsonStr.length(); i++) {
                        if (jsonStr.charAt(i) == '{') depth++;
                        else if (jsonStr.charAt(i) == '}') { depth--; if (depth == 0) { end = i; break; } }
                    }
                    fieldsBlock = jsonStr.substring(brace, end + 1);
                }
            } else {
                // 兼容扁平格式：直接使用整个 body
                fieldsBlock = jsonStr;
            }

            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("company_name", extractJsonString(fieldsBlock, "company_name"));
            fields.put("body_text", extractJsonString(fieldsBlock, "body_text"));
            fields.put("contact_person", extractJsonString(fieldsBlock, "contact_person"));
            fields.put("contact_phone", extractJsonString(fieldsBlock, "contact_phone"));
            fields.put("recipient", extractJsonString(fieldsBlock, "recipient"));
            fields.put("recipient_phone", extractJsonString(fieldsBlock, "recipient_phone"));
            fields.put("email", extractJsonString(fieldsBlock, "email"));
            fields.put("whiteout_bottom", extractJsonString(fieldsBlock, "whiteout_bottom", "280"));

            if (ConfigManager.saveConfig(name, fields)) {
                // 同时设为默认
                ConfigManager.setDefaultConfig(name);
                String json = "{\"success\":true,\"name\":\"" + escapeJson(name) + "\"}";
                sendJson(exchange, json);
            } else {
                sendJsonError(exchange, 500, "保存失败");
            }
        }

        private void handleDelete(HttpExchange exchange, String name) throws IOException {
            if (name.isEmpty()) {
                sendJsonError(exchange, 400, "缺少配置名称");
                return;
            }
            if (ConfigManager.deleteConfig(name)) {
                String json = "{\"success\":true}";
                sendJson(exchange, json);
            } else {
                sendJsonError(exchange, 404, "配置不存在或删除失败");
            }
        }

        private String fieldsToJson(Map<String, String> fields) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(e.getKey())).append("\":\"")
                  .append(escapeJson(e.getValue())).append("\"");
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }

        private String extractJsonString(String json, String key) {
            return extractJsonString(json, key, "");
        }
        private String extractJsonString(String json, String key, String def) {
            // 先从 fields 内部找
            int fieldsIdx = json.indexOf("\"fields\"");
            String searchFrom = (fieldsIdx >= 0) ? json.substring(fieldsIdx) : json;

            String pattern = "\"" + key + "\":\"";
            int idx = searchFrom.indexOf(pattern);
            if (idx < 0) {
                // 尝试数字值
                pattern = "\"" + key + "\":";
                idx = searchFrom.indexOf(pattern);
                if (idx < 0) return def;
                int start = idx + pattern.length();
                while (start < searchFrom.length() && searchFrom.charAt(start) == ' ' || searchFrom.charAt(start) == '\n') start++;
                int end = searchFrom.indexOf(',', start);
                if (end < 0) end = searchFrom.indexOf('}', start);
                if (end < 0) return def;
                return searchFrom.substring(start, end).trim();
            }
            int start = idx + pattern.length();
            int end = searchFrom.indexOf('"', start);
            if (end < 0) return def;
            return searchFrom.substring(start, end);
        }
    }

    // ================================================================
    // 内部数据类
    // ================================================================

    static class MultipartEntry {
        String name = "";
        String filename = "";
        byte[] data = new byte[0];
    }
}

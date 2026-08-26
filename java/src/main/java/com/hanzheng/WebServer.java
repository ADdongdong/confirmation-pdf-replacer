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
 * API 契约与 Python web_form_server.py 完全对齐，复用 React 前端构建产物。
 *
 *   GET  /                            服务 React 前端
 *   GET  /upload /config /batch       服务 React 前端（HashRouter 内部路由）
 *   GET  /static/react/*              React 静态资源
 *
 *   单文件处理：
 *   POST /generate                    单文件处理（multipart，对齐 Python）
 *   POST /preview                     预览首页 PNG（返回 JSON）
 *   GET  /preview-img/<filename>      PNG 预览图
 *   GET  /download/<filename>         下载单文件结果
 *
 *   批量处理：
 *   POST /batch/upload                上传多个 PDF（multipart pdf_files）
 *   GET  /batch/preview/<token>/<fn>  批量文件缩略图
 *   POST /batch/re-recognize          重新识别单个文件收函单位（fileId）
 *   POST /batch/generate              批量生成（JSON）
 *   GET  /batch/download/<token>      下载 ZIP
 *
 *   配置管理：
 *   GET  /config/list                 列出所有配置
 *   GET  /config/load/<filename>      加载配置
 *   POST /config/save                 保存配置
 *   POST /config/delete/<filename>    删除配置（DELETE 兼容）
 *   GET  /config/default              获取默认配置
 *   POST /config/preview              样本 PDF 覆盖区域校准预览
 */
public class WebServer {

    private static final String VERSION = "5.0";
    private static final int PORT = 8889;

    // ---- React 前端产物路径（与 python/static/react 共享） ----
    private static final File REACT_DIR = new File("../python/static/react");

    // ---- 临时目录（预览 PNG、批量会话） ----
    private static final File TEMP_DIR = new File("temp");

    // ---- 批量作业存储 ----
    private static final ConcurrentHashMap<String, BatchJob> batchJobs = new ConcurrentHashMap<>();

    // ---- PNG 预览缓存（key → byte[]） ----
    private static final ConcurrentHashMap<String, byte[]> pngCache = new ConcurrentHashMap<>();

    // ---- 单文件结果缓存（key → byte[]） ----
    private static final ConcurrentHashMap<String, byte[]> resultCache = new ConcurrentHashMap<>();


    public static void main(String[] args) throws IOException {
        ConfigManager.ensureDir();
        if (!TEMP_DIR.exists()) TEMP_DIR.mkdirs();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // ---- 页面 + React 静态资源 ----
        server.createContext("/", new PageHandler());

        // ---- 单文件处理 ----
        server.createContext("/generate", new GenerateHandler());
        server.createContext("/preview", new PreviewHandler());
        server.createContext("/preview-img", new PreviewImgHandler());
        server.createContext("/download", new DownloadHandler());

        // ---- 批量处理 ----
        server.createContext("/batch", new BatchHandler());

        // ---- 配置管理 ----
        server.createContext("/config", new ConfigHandler());

        // ---- 静态资源 ----
        server.createContext("/static", new StaticHandler());

        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("=================================================");
        System.out.println("  投行函证 PDF 头部替换工具 v" + VERSION);
        System.out.println("  服务已启动: http://localhost:" + PORT);
        System.out.println("  主入口:     http://localhost:" + PORT + "/upload");
        System.out.println("=================================================");
    }

    // ================================================================
    // 工具方法
    // ================================================================

    private static void setCors(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendJson(HttpExchange exchange, String json) throws IOException {
        setCors(exchange);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

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

    private static void sendHtml(HttpExchange exchange, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendBytes(HttpExchange exchange, String contentType, byte[] data) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private static void sendText(HttpExchange exchange, int code, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
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

    /** 从请求 URI 中提取路径（去掉 query string） */
    private static String getPath(HttpExchange exchange) {
        return exchange.getRequestURI().getPath();
    }

    /** 生成唯一 token（uuid hex 12 位） */
    private static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    /** 解析 URL 编码表单 */
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

    /** 解析 multipart/form-data，返回 name → 条目列表（支持同名多值，如多个 pdf_files） */
    private static Map<String, List<MultipartEntry>> parseMultipart(String contentType, byte[] body) {
        Map<String, List<MultipartEntry>> result = new LinkedHashMap<>();
        if (body == null || body.length == 0) return result;
        try {
            String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
            boundary = boundary.replace("\"", "");
            byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.UTF_8);

            int pos = 0;
            byte[] endBoundary = ("--" + boundary + "--").getBytes(StandardCharsets.UTF_8);
            while (pos < body.length) {
                // 找当前 part 的边界起始（--boundary）
                int start = indexOf(body, boundaryBytes, pos);
                if (start < 0) break;
                // 跳过 boundary 行末尾（boundary + \r\n），定位到 header 起始
                int headerStart = start + boundaryBytes.length;
                if (headerStart < body.length && body[headerStart] == '\r') headerStart += 2;
                int headerEnd = indexOf(body, "\r\n\r\n".getBytes(), headerStart);
                if (headerEnd < 0) break;
                int contentStart = headerEnd + 4;

                // 找下一个 part 的边界（--boundary）或结束边界（--boundary--）
                int nextBoundary = indexOf(body, boundaryBytes, contentStart);
                if (nextBoundary < 0) break;
                int contentEnd;
                boolean isEnd = false;
                if (body[nextBoundary + boundaryBytes.length] == '-' &&
                    body[nextBoundary + boundaryBytes.length + 1] == '-') {
                    // 结束边界 --boundary--
                    contentEnd = nextBoundary - 2;
                    isEnd = true;
                } else {
                    contentEnd = nextBoundary - 2;
                }
                if (contentEnd > contentStart) {
                    String headerStr = new String(body, headerStart, headerEnd - headerStart, StandardCharsets.UTF_8);
                    String name = "";
                    int nameIdx = headerStr.indexOf("name=\"");
                    if (nameIdx >= 0) {
                        int nameEnd = headerStr.indexOf("\"", nameIdx + 6);
                        if (nameEnd > 0) name = headerStr.substring(nameIdx + 6, nameEnd);
                    }
                    String filename = "";
                    int fnIdx = headerStr.indexOf("filename=\"");
                    if (fnIdx >= 0) {
                        int fnEnd = headerStr.indexOf("\"", fnIdx + 10);
                        if (fnEnd > 0) filename = headerStr.substring(fnIdx + 10, fnEnd);
                    }
                    MultipartEntry entry = new MultipartEntry();
                    entry.name = name;
                    entry.filename = filename;
                    entry.data = Arrays.copyOfRange(body, contentStart, contentEnd);
                    result.computeIfAbsent(name, k -> new ArrayList<>()).add(entry);
                }
                if (isEnd) break;
                // 下一个 part 从当前边界（--boundary）起始继续
                pos = nextBoundary;
            }
        } catch (Exception e) {
            System.err.println("[parseMultipart] Error: " + e.getMessage());
        }
        return result;
    }

    /** 取单个字段值（无/空返回 null） */
    private static MultipartEntry getSinglePart(Map<String, List<MultipartEntry>> parts, String name) {
        List<MultipartEntry> list = parts.get(name);
        if (list == null || list.isEmpty()) return null;
        return list.get(0);
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

    private static String getPartValue(Map<String, List<MultipartEntry>> parts, String key) {
        return getPartValue(parts, key, "");
    }
    private static String getPartValue(Map<String, List<MultipartEntry>> parts, String key, String def) {
        List<MultipartEntry> list = parts.get(key);
        if (list == null || list.isEmpty()) return def;
        MultipartEntry e = list.get(0);
        if (e.data == null || e.data.length == 0) return def;
        return new String(e.data, StandardCharsets.UTF_8).trim();
    }

    /** 简单 JSON 提取字符串值（兼容 Python requests 默认序列化："key": "value"） */
    private static String extractJsonString(String json, String key, String def) {
        // 优先匹配带引号字符串值："key":"value"
        String pattern = "\"" + key + "\":\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) {
            // fallback：key 后可能有空格，value 可能带/不带引号
            pattern = "\"" + key + "\":";
            idx = json.indexOf(pattern);
            if (idx < 0) return def;
            int start = idx + pattern.length();
            // 跳过空白
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\n'
                    || json.charAt(start) == '\r' || json.charAt(start) == '\t')) start++;
            int end;
            if (start < json.length() && json.charAt(start) == '"') {
                // value 是带引号字符串：取到下一个非转义 "
                end = -1;
                for (int i = start + 1; i < json.length(); i++) {
                    if (json.charAt(i) == '\\') { i++; continue; }
                    if (json.charAt(i) == '"') { end = i; break; }
                }
                if (end < 0) return def;
                return json.substring(start + 1, end);
            } else {
                // value 是数字/null/true/false：取到 , 或 }
                end = json.indexOf(',', start);
                if (end < 0) end = json.indexOf('}', start);
                if (end < 0) return def;
                return json.substring(start, end).trim();
            }
        }
        int start = idx + pattern.length();
        int end = json.indexOf('"', start);
        if (end < 0) return def;
        return json.substring(start, end);
    }

    /** 清理过期作业 */
    private static void cleanupExpired() {
        long now = System.currentTimeMillis();
        batchJobs.entrySet().removeIf(e -> now - e.getValue().createdAt > 3600000L);
    }

    /** 渲染首页 PNG 并缓存，返回缓存 key（即文件名） */
    private static String renderAndCachePng(byte[] pdfData, String prefix, float zoom) {
        String name = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".png";
        byte[] png = PdfPreviewUtil.renderFirstPagePng(pdfData, zoom);
        if (png == null) return null;
        pngCache.put(name, png);
        return name;
    }

    // ================================================================
    // 页面 + React 静态资源
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

            // 所有页面路径都返回 React 前端（HashRouter 内部处理）
            sendHtml(exchange, readReactIndex());
        }
    }

    /** 读取 React 构建产物 index.html（优先 ../python/static/react，回退 classpath） */
    private static String readReactIndex() throws IOException {
        File f = new File(REACT_DIR, "index.html");
        if (f.exists()) {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        }
        // 回退：classpath 下的 index.html
        try (InputStream is = WebServer.class.getClassLoader().getResourceAsStream("index.html")) {
            if (is == null) return "<html><body>React 前端未构建，请先运行 npm run build</body></html>";
            java.util.Scanner s = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
            return s.hasNext() ? s.next() : "";
        }
    }

    // ================================================================
    // React 静态资源（/static/react/assets/...）
    // ================================================================
    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = getPath(exchange); // /static/react/assets/js/main.js
            // 去掉 /static/react/ 前缀，剩余相对 REACT_DIR 的路径（如 assets/js/main.js）
            String relative = path.startsWith("/static/react/") ? path.substring("/static/react/".length()) : path;
            if (relative.startsWith("/")) relative = relative.substring(1);
            // 去掉可能的 .. 防目录穿越
            relative = relative.replace("..", "");
            File f = new File(REACT_DIR, relative);
            if (f.exists() && f.isFile()) {
                byte[] data = Files.readAllBytes(f.toPath());
                String ct = guessContentType(relative);
                sendBytes(exchange, ct, data);
                return;
            }
            // 回退 classpath static
            try (InputStream is = WebServer.class.getClassLoader().getResourceAsStream("static/" + relative)) {
                if (is != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                    sendBytes(exchange, guessContentType(relative), baos.toByteArray());
                    return;
                }
            }
            sendText(exchange, 404, "Not found");
        }

        private String guessContentType(String path) {
            if (path.endsWith(".js")) return "application/javascript";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".svg")) return "image/svg+xml";
            if (path.endsWith(".json")) return "application/json";
            if (path.endsWith(".woff2")) return "font/woff2";
            if (path.endsWith(".woff")) return "font/woff";
            return "application/octet-stream";
        }
    }

    // ================================================================
    // 单文件预览（POST /preview → JSON + PNG）
    // ================================================================
    static class PreviewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCors(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJsonError(exchange, 405, "Method not allowed");
                return;
            }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            byte[] body = readBody(exchange);
            if (contentType == null || !contentType.contains("multipart/form-data")) {
                sendJsonError(exchange, 400, "请使用 multipart/form-data");
                return;
            }
            Map<String, List<MultipartEntry>> parts = parseMultipart(contentType, body);
            MultipartEntry pdfEntry = getSinglePart(parts, "pdf_file");
            if (pdfEntry == null || pdfEntry.data.length == 0) {
                sendJsonError(exchange, 400, "未收到 PDF 文件");
                return;
            }
            try {
                float[] size = PdfPreviewUtil.getFirstPageSize(pdfEntry.data);
                if (size == null) {
                    sendJsonError(exchange, 400, "PDF 无页面");
                    return;
                }
                String imgName = renderAndCachePng(pdfEntry.data, "preview", PdfPreviewUtil.ZOOM_PREVIEW);
                if (imgName == null) {
                    sendJsonError(exchange, 500, "预览图生成失败");
                    return;
                }
                byte[] png = pngCache.get(imgName);
                int imgW = 0, imgH = 0;
                try {
                    javax.imageio.stream.ImageInputStream iis =
                        javax.imageio.ImageIO.createImageInputStream(new ByteArrayInputStream(png));
                    java.util.Iterator<javax.imageio.ImageReader> it =
                        javax.imageio.ImageIO.getImageReaders(iis);
                    if (it.hasNext()) {
                        javax.imageio.ImageReader r = it.next();
                        r.setInput(iis);
                        imgW = r.getWidth(0);
                        imgH = r.getHeight(0);
                        r.dispose();
                    }
                    iis.close();
                } catch (Exception ignored) {}

                String json = "{\"success\":true,\"imageUrl\":\"/preview-img/" + imgName + "\","
                        + "\"pageWidth\":" + Math.round(size[0] * 10) / 10.0 + ","
                        + "\"pageHeight\":" + Math.round(size[1] * 10) / 10.0 + ","
                        + "\"imageWidth\":" + imgW + ","
                        + "\"imageHeight\":" + imgH + "}";
                sendJson(exchange, json);
            } catch (Exception e) {
                sendJsonError(exchange, 500, "预览生成失败: " + e.getMessage());
            }
        }
    }

    // ================================================================
    // 单文件生成（POST /generate）
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
            if (contentType == null || !contentType.contains("multipart/form-data")) {
                sendJsonError(exchange, 400, "请使用 multipart/form-data 上传 PDF 文件");
                return;
            }
            Map<String, List<MultipartEntry>> parts = parseMultipart(contentType, body);
            MultipartEntry pdfEntry = getSinglePart(parts, "pdf_file");
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
            req.postalCode = getPartValue(parts, "postal_code");
            req.fax = getPartValue(parts, "fax");
            req.email = getPartValue(parts, "email");
            req.returnAddress = getPartValue(parts, "return_address");
            req.whiteoutBottom = parseNullableDouble(getPartValue(parts, "whiteout_bottom"));
            req.footerHeight = parseNullableDouble(getPartValue(parts, "footer_height"));

            processAndRespond(exchange, pdfEntry.data, pdfEntry.filename, req);
        }

        private Double parseNullableDouble(String s) {
            if (s == null || s.trim().isEmpty()) return null;
            try {
                return Double.parseDouble(s.trim());
            } catch (Exception e) {
                return null;
            }
        }

        private void processAndRespond(HttpExchange exchange, byte[] pdfData, String filename,
                                        HanzhengRequest req) throws IOException {
            File tempDir = TEMP_DIR;
            String baseName = System.currentTimeMillis() + "_" + (filename != null ? sanitize(filename) : "input.pdf");
            File inputFile = new File(tempDir, baseName);
            String outputKey = generateToken();
            File outputFile = new File(tempDir, "out_" + outputKey + ".pdf");

            try {
                Files.write(inputFile.toPath(), pdfData);
                req.inputPath = inputFile.getAbsolutePath();
                req.outputPath = outputFile.getAbsolutePath();

                new PdfProcessor().process(req);

                byte[] result = Files.readAllBytes(outputFile.toPath());
                resultCache.put(outputKey, result);

                String json = "{\"success\":true,\"filename\":\"" + escapeJson(filename) + "\","
                        + "\"path\":\"" + outputKey + "\","
                        + "\"company_name\":\"" + escapeJson(req.companyName) + "\"}";
                sendJson(exchange, json);
            } catch (Exception e) {
                sendJsonError(exchange, 500, "处理失败: " + e.getMessage());
                e.printStackTrace();
            } finally {
                inputFile.delete();
                outputFile.delete();
            }
        }

        private String sanitize(String s) {
            return s.replaceAll("[\\\\/:*?\"<>|]", "_");
        }
    }

    // ================================================================
    // 下载（GET /download/<filename>）
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
            if (data == null) {
                sendText(exchange, 404, "文件不存在或已过期");
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"processed_" + key + ".pdf\"");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
            resultCache.remove(key);
        }
    }

    // ================================================================
    // 预览图 PNG（GET /preview-img/<filename>）
    // ================================================================
    static class PreviewImgHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCors(exchange);
            String path = getPath(exchange);
            // /preview-img/<name>
            String name = path.replace("/preview-img/", "").replace("/preview-img", "");
            if (name.isEmpty()) {
                sendText(exchange, 400, "缺少文件名");
                return;
            }
            byte[] data = pngCache.get(name);
            if (data == null) {
                sendText(exchange, 404, "图片不存在或已过期");
                return;
            }
            sendBytes(exchange, "image/png", data);
        }
    }

    // ================================================================
    // 批量处理（/batch/*）
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

            if (path.equals("/batch/upload")) {
                if ("POST".equals(method)) handleBatchUpload(exchange);
                else sendJsonError(exchange, 405, "Method not allowed");
            } else if (path.startsWith("/batch/preview/")) {
                handleBatchPreviewImg(exchange, path);
            } else if (path.equals("/batch/re-recognize")) {
                if ("POST".equals(method)) handleReRecognize(exchange);
                else sendJsonError(exchange, 405, "Method not allowed");
            } else if (path.equals("/batch/generate")) {
                if ("POST".equals(method)) handleBatchGenerate(exchange);
                else sendJsonError(exchange, 405, "Method not allowed");
            } else if (path.startsWith("/batch/download/")) {
                if ("GET".equals(method)) handleBatchDownload(exchange, path);
                else sendJsonError(exchange, 405, "Method not allowed");
            } else {
                sendJsonError(exchange, 404, "Not found");
            }
        }

        /** POST /batch/upload */
        private void handleBatchUpload(HttpExchange exchange) throws IOException {
            cleanupExpired();
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            byte[] body = readBody(exchange);
            if (contentType == null || !contentType.contains("multipart/form-data")) {
                sendJsonError(exchange, 400, "请使用 multipart/form-data");
                return;
            }
            Map<String, List<MultipartEntry>> parts = parseMultipart(contentType, body);

            // 收集所有 pdf_files（Python 用 pdf_files 字段，可重复；Java parseMultipart 保留同名多值）
            List<MultipartEntry> files = new ArrayList<>();
            List<MultipartEntry> pdfList = parts.get("pdf_files");
            if (pdfList != null) {
                for (MultipartEntry e : pdfList) {
                    if (e.filename != null && !e.filename.isEmpty()
                            && e.data.length > 0 && e.filename.toLowerCase().endsWith(".pdf")) {
                        files.add(e);
                    }
                }
            }
            if (files.size() > 20) {
                sendJsonError(exchange, 400, "单次最多上传 20 个文件");
                return;
            }
            if (files.isEmpty()) {
                sendJsonError(exchange, 400, "没有有效的 PDF 文件");
                return;
            }

            String token = generateToken();
            BatchJob job = new BatchJob(token);

            StringBuilder filesJson = new StringBuilder();
            boolean first = true;

            for (MultipartEntry part : files) {
                String id = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                File pdfFile = new File(TEMP_DIR, "batch_" + token + "_" + id + ".pdf");
                Files.write(pdfFile.toPath(), part.data);

                BatchJob.FileEntry fe = new BatchJob.FileEntry(id, part.filename);
                try {
                    String[] r = RecipientExtractor.extract(pdfFile);
                    fe.recipient = r[0];
                    fe.confidence = normalizeConfidence(r[1]);
                } catch (Exception e) {
                    fe.recipient = "";
                    fe.confidence = "low";
                }
                // 生成缩略图
                String thumbName = renderAndCachePng(part.data, "thumb_" + token + "_" + id, PdfPreviewUtil.ZOOM_THUMB);
                if (thumbName != null) {
                    fe.previewUrl = "/batch/preview/" + token + "/" + thumbName;
                }
                job.files.put(id, fe);

                if (!first) filesJson.append(",");
                first = false;
                filesJson.append("{\"id\":\"").append(id).append("\",")
                    .append("\"name\":\"").append(escapeJson(part.filename)).append("\",")
                    .append("\"recipient\":\"").append(escapeJson(fe.recipient)).append("\",")
                    .append("\"confidence\":\"").append(fe.confidence).append("\",")
                    .append("\"previewUrl\":").append(fe.previewUrl != null ? "\"" + fe.previewUrl + "\"" : "null")
                    .append("}");
            }

            batchJobs.put(token, job);
            String json = "{\"success\":true,\"token\":\"" + token + "\","
                    + "\"files\":[" + filesJson + "]}";
            sendJson(exchange, json);
        }

        private String normalizeConfidence(String c) {
            if (c == null || c.isEmpty()) return "low";
            return "low".equals(c) ? "low" : "high"; // medium → high（前端只区分 low/high）
        }

        /** GET /batch/preview/<token>/<filename> */
        private void handleBatchPreviewImg(HttpExchange exchange, String path) throws IOException {
            String[] seg = path.split("/"); // ["", "batch", "preview", token, filename]
            if (seg.length < 5) {
                sendText(exchange, 404, "Not found");
                return;
            }
            String filename = seg[4];
            byte[] data = pngCache.get(filename);
            if (data == null) {
                sendText(exchange, 404, "图片不存在");
                return;
            }
            sendBytes(exchange, "image/png", data);
        }

        /** POST /batch/re-recognize */
        private void handleReRecognize(HttpExchange exchange) throws IOException {
            byte[] body = readBody(exchange);
            String jsonStr = new String(body, StandardCharsets.UTF_8);
            String token = extractJsonString(jsonStr, "token", "");
            String fileId = extractJsonString(jsonStr, "fileId", "");

            BatchJob job = batchJobs.get(token);
            if (job == null) {
                sendJsonError(exchange, 404, "会话已过期");
                return;
            }
            BatchJob.FileEntry fe = job.files.get(fileId);
            if (fe == null) {
                sendJsonError(exchange, 400, "文件不存在");
                return;
            }
            File pdfFile = new File(TEMP_DIR, "batch_" + token + "_" + fileId + ".pdf");
            try {
                String[] r = RecipientExtractor.extract(pdfFile);
                fe.recipient = r[0];
                fe.confidence = normalizeConfidence(r[1]);
                String response = "{\"success\":true,\"recipient\":\"" + escapeJson(r[0]) + "\","
                        + "\"confidence\":\"" + fe.confidence + "\"}";
                sendJson(exchange, response);
            } catch (Exception e) {
                sendJsonError(exchange, 500, "识别失败: " + e.getMessage());
            }
        }

        /** POST /batch/generate */
        private void handleBatchGenerate(HttpExchange exchange) throws IOException {
            byte[] body = readBody(exchange);
            String jsonStr = new String(body, StandardCharsets.UTF_8);

            String token = extractJsonString(jsonStr, "token", "");
            if (token.isEmpty()) {
                sendJsonError(exchange, 400, "缺少 token");
                return;
            }
            BatchJob job = batchJobs.get(token);
            if (job == null) {
                sendJsonError(exchange, 404, "会话已过期");
                return;
            }

            // 解析 contacts（中文 key 嵌套）
            Map<String, String> contacts = new LinkedHashMap<>();
            String contactsBlock = extractSubJson(jsonStr, "contacts");
            if (contactsBlock != null) {
                Map<String, Object> parsed = ConfigManager.parseJsonObject(contactsBlock);
                for (Map.Entry<String, Object> e : parsed.entrySet()) {
                    contacts.put(e.getKey(), e.getValue() == null ? "" : String.valueOf(e.getValue()));
                }
            }
            job.contacts = contacts;

            String bodyText = extractJsonString(jsonStr, "body_text", "").trim();
            if (bodyText.isEmpty()) {
                sendJsonError(exchange, 400, "请输入函证正文");
                return;
            }
            job.bodyText = bodyText;
            job.whiteoutBottom = parseNullableDouble(extractJsonString(jsonStr, "whiteout_bottom", ""));
            job.footerHeight = parseNullableDouble(extractJsonString(jsonStr, "footer_height", ""));

            // recipients 覆盖 {fileId: recipient}
            Map<String, String> recipients = new LinkedHashMap<>();
            String recBlock = extractSubJson(jsonStr, "recipients");
            if (recBlock != null) {
                Map<String, Object> parsed = ConfigManager.parseJsonObject(recBlock);
                for (Map.Entry<String, Object> e : parsed.entrySet()) {
                    recipients.put(e.getKey(), e.getValue() == null ? "" : String.valueOf(e.getValue()));
                }
            }

            // 输出
            List<String> resultsNames = new ArrayList<>();
            StringBuilder errorsJson = new StringBuilder();
            boolean errFirst = true;

            for (BatchJob.FileEntry fe : job.files.values()) {
                String recipient = recipients.containsKey(fe.id) ? recipients.get(fe.id).trim()
                        : (fe.recipient != null ? fe.recipient.trim() : "");
                if (recipient.isEmpty()) {
                    if (!errFirst) errorsJson.append(",");
                    errFirst = false;
                    errorsJson.append("{\"file\":\"").append(escapeJson(fe.name)).append("\",\"error\":\"收函单位为空\"}");
                    continue;
                }

                File pdfFile = new File(TEMP_DIR, "batch_" + token + "_" + fe.id + ".pdf");
                String safeName = sanitizeFilename(fe.name);
                String outKey = "fixed_" + fe.id + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 4);
                File outPdf = new File(TEMP_DIR, outKey + ".pdf");

                try {
                    HanzhengRequest req = new HanzhengRequest();
                    req.companyName = recipient;
                    req.bodyText = bodyText;
                    req.contactPerson = contacts.get("项目联系人");
                    req.contactPhone = contacts.get("项目联系人电话");
                    req.recipient = contacts.get("收件人");
                    req.recipientPhone = contacts.get("收件人电话");
                    req.postalCode = contacts.get("邮编");
                    req.fax = contacts.get("传真");
                    req.email = contacts.get("邮箱");
                    req.returnAddress = contacts.get("回函地址");
                    req.whiteoutBottom = job.whiteoutBottom;
                    req.footerHeight = job.footerHeight;
                    req.inputPath = pdfFile.getAbsolutePath();
                    req.outputPath = outPdf.getAbsolutePath();

                    new PdfProcessor().process(req);
                    byte[] result = Files.readAllBytes(outPdf.toPath());
                    resultCache.put(outKey, result);

                    BatchJob.ResultEntry re = new BatchJob.ResultEntry(fe.name, outKey + ".pdf", recipient);
                    job.results.put(outKey, re);
                    resultsNames.add(re.file);
                    outPdf.delete();
                } catch (Exception e) {
                    if (!errFirst) errorsJson.append(",");
                    errFirst = false;
                    errorsJson.append("{\"file\":\"").append(escapeJson(fe.name)).append("\",\"error\":\"")
                            .append(escapeJson(e.getMessage() == null ? "处理失败" : e.getMessage())).append("\"}");
                    outPdf.delete();
                }
            }

            if (resultsNames.isEmpty()) {
                String err = errorsJson.length() == 0 ? "所有文件处理失败" : "";
                sendJson(exchange, "{\"success\":false,\"error\":\"所有文件处理失败\",\"errors\":[" + errorsJson + "]}");
                return;
            }

            // 打包 ZIP
            String zipName = "hanzheng_batch_" + token + ".zip";
            File zipFile = new File(TEMP_DIR, zipName);
            try (ZipOutputStream zos = new ZipOutputStream(new java.io.FileOutputStream(zipFile))) {
                for (String file : resultsNames) {
                    byte[] data = resultCache.get(file.replace(".pdf", ""));
                    if (data == null) continue;
                    ZipEntry ze = new ZipEntry(file);
                    zos.putNextEntry(ze);
                    zos.write(data);
                    zos.closeEntry();
                }
            }
            job.zipPath = zipFile.getAbsolutePath();
            job.zipName = zipName;
            job.status = "done";

            String errorsField = errorsJson.length() == 0 ? "null" : "[" + errorsJson + "]";
            String json = "{\"success\":true,\"downloadToken\":\"" + token + "\","
                    + "\"total\":" + resultsNames.size() + ","
                    + "\"errors\":" + errorsField + "}";
            sendJson(exchange, json);
        }

        private Double parseNullableDouble(String s) {
            if (s == null || s.trim().isEmpty()) return null;
            try {
                return Double.parseDouble(s.trim());
            } catch (Exception e) {
                return null;
            }
        }

        /** GET /batch/download/<token> */
        private void handleBatchDownload(HttpExchange exchange, String path) throws IOException {
            String token = path.replace("/batch/download/", "").replace("/batch/download", "");
            if (token.isEmpty()) {
                sendText(exchange, 400, "缺少 token");
                return;
            }
            BatchJob job = batchJobs.get(token);
            if (job == null || job.zipPath == null || !new File(job.zipPath).exists()) {
                sendText(exchange, 404, "文件不存在或已过期");
                return;
            }
            byte[] data = Files.readAllBytes(new File(job.zipPath).toPath());
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"" + job.zipName + "\"");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }

        /** 从 JSON 中提取一层嵌套子对象原始文本（含花括号） */
        private String extractSubJson(String json, String key) {
            String pattern = "\"" + key + "\":";
            int idx = json.indexOf(pattern);
            if (idx < 0) return null;
            int brace = json.indexOf('{', idx + pattern.length());
            if (brace < 0) return null;
            int depth = 0, end = brace;
            for (int i = brace; i < json.length(); i++) {
                if (json.charAt(i) == '{') depth++;
                else if (json.charAt(i) == '}') { depth--; if (depth == 0) { end = i; break; } }
            }
            return json.substring(brace, end + 1);
        }

        private String sanitizeFilename(String name) {
            String base = name.replaceAll("\\\\.[a-zA-Z0-9]+$", "");
            return base.replaceAll("[^\\p{L}\\p{N}_.\\- ]", "_");
        }
    }

    // ================================================================
    // 配置管理（/config/*）
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

            if ("GET".equals(method) && path.equals("/config/default")) {
                handleGetDefault(exchange);
            } else if ("GET".equals(method) && path.equals("/config/list")) {
                handleList(exchange);
            } else if ("GET".equals(method) && path.startsWith("/config/load/")) {
                handleLoad(exchange, path.substring("/config/load/".length()));
            } else if ("POST".equals(method) && path.equals("/config/save")) {
                handleSave(exchange);
            } else if (("POST".equals(method) || "DELETE".equals(method)) && path.startsWith("/config/delete/")) {
                handleDelete(exchange, path.substring("/config/delete/".length()));
            } else if ("POST".equals(method) && path.equals("/config/preview")) {
                handleConfigPreview(exchange);
            } else {
                sendHtml(exchange, readReactIndex());
            }
        }

        /** GET /config/default */
        private void handleGetDefault(HttpExchange exchange) throws IOException {
            Map<String, Object> cfg = ConfigManager.loadDefaultConfig();
            if (cfg == null) {
                sendJson(exchange, "{\"success\":true,\"config\":null}");
                return;
            }
            String json = "{\"success\":true,\"config\":" + ConfigManager.toJsonString(cfg) + "}";
            sendJson(exchange, json);
        }

        /** GET /config/list */
        private void handleList(HttpExchange exchange) throws IOException {
            List<String> filenames = ConfigManager.listConfigFiles();
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (String fname : filenames) {
                Map<String, Object> cfg = ConfigManager.loadConfig(fname);
                String name = cfg != null && cfg.get("name") != null
                        ? String.valueOf(cfg.get("name")) : fname.replace(".json", "");
                String bodyPreview = cfg != null && cfg.get("body_text") != null
                        ? String.valueOf(cfg.get("body_text")) : "";
                if (bodyPreview.length() > 80) bodyPreview = bodyPreview.substring(0, 80);
                boolean hasWhiteout = cfg != null && cfg.get("whiteout_bottom") != null
                        && !String.valueOf(cfg.get("whiteout_bottom")).isEmpty()
                        && !"null".equals(String.valueOf(cfg.get("whiteout_bottom")));
                if (!first) sb.append(",");
                first = false;
                sb.append("{\"filename\":\"").append(escapeJson(fname)).append("\",")
                    .append("\"name\":\"").append(escapeJson(name)).append("\",")
                    .append("\"body_text\":\"").append(escapeJson(bodyPreview)).append("\",")
                    .append("\"has_whiteout\":").append(hasWhiteout).append("}");
            }
            sendJson(exchange, "{\"success\":true,\"configs\":[" + sb + "]}");
        }

        /** GET /config/load/<filename> */
        private void handleLoad(HttpExchange exchange, String filename) throws IOException {
            if (filename.isEmpty()) {
                sendJsonError(exchange, 400, "缺少配置名称");
                return;
            }
            Map<String, Object> cfg = ConfigManager.loadConfig(filename);
            if (cfg == null) {
                sendJsonError(exchange, 404, "配置不存在");
                return;
            }
            String defaultName = ConfigManager.getDefaultConfigName();
            String currentName = cfg.get("name") != null ? String.valueOf(cfg.get("name")) : filename.replace(".json", "");
            cfg.put("is_default", defaultName.equals(currentName));
            sendJson(exchange, "{\"success\":true,\"config\":" + ConfigManager.toJsonString(cfg) + "}");
        }

        /** POST /config/save */
        private void handleSave(HttpExchange exchange) throws IOException {
            byte[] body = readBody(exchange);
            String jsonStr = new String(body, StandardCharsets.UTF_8);

            String name = extractJsonString(jsonStr, "name", "");
            if (name.isEmpty()) {
                sendJsonError(exchange, 400, "缺少配置名称");
                return;
            }
            String bodyText = extractJsonString(jsonStr, "body_text", "");
            String whiteoutBottom = extractJsonString(jsonStr, "whiteout_bottom", "");
            String footerHeight = extractJsonString(jsonStr, "footer_height", "");
            boolean isDefault = "true".equals(extractJsonString(jsonStr, "is_default", "false"));

            Map<String, String> contacts = new LinkedHashMap<>();
            String contactsBlock = extractSubJson(jsonStr, "contacts");
            if (contactsBlock != null) {
                Map<String, Object> parsed = ConfigManager.parseJsonObject(contactsBlock);
                for (Map.Entry<String, Object> e : parsed.entrySet()) {
                    contacts.put(e.getKey(), e.getValue() == null ? "" : String.valueOf(e.getValue()));
                }
            }

            if (ConfigManager.saveConfig(name, contacts, bodyText, whiteoutBottom, footerHeight, isDefault)) {
                if (isDefault) {
                    ConfigManager.setDefaultConfig(name);
                } else {
                    ConfigManager.clearDefaultConfig(name);
                }
                sendJson(exchange, "{\"success\":true,\"filename\":\"" + escapeJson(name) + ".json\"}");
            } else {
                sendJsonError(exchange, 500, "保存失败");
            }
        }

        /** POST/DELETE /config/delete/<filename> */
        private void handleDelete(HttpExchange exchange, String filename) throws IOException {
            if (filename.isEmpty()) {
                sendJsonError(exchange, 400, "缺少配置名称");
                return;
            }
            if (ConfigManager.deleteConfig(filename)) {
                sendJson(exchange, "{\"success\":true}");
            } else {
                sendJsonError(exchange, 404, "配置不存在或删除失败");
            }
        }

        /** POST /config/preview — 样本 PDF 覆盖区域校准预览 */
        private void handleConfigPreview(HttpExchange exchange) throws IOException {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            byte[] body = readBody(exchange);
            if (contentType == null || !contentType.contains("multipart/form-data")) {
                sendJsonError(exchange, 400, "请使用 multipart/form-data");
                return;
            }
            Map<String, List<MultipartEntry>> parts = parseMultipart(contentType, body);
            MultipartEntry pdfEntry = getSinglePart(parts, "pdf_file");
            if (pdfEntry == null || pdfEntry.data.length == 0) {
                sendJsonError(exchange, 400, "未收到 PDF 文件");
                return;
            }
            try {
                float[] size = PdfPreviewUtil.getFirstPageSize(pdfEntry.data);
                if (size == null) {
                    sendJsonError(exchange, 400, "PDF 无页面");
                    return;
                }
                float pageW = size[0], pageH = size[1];

                // 检测表格起始 y（用 FormatExtractor）
                File tmpPdf = new File(TEMP_DIR, "cfg_" + generateToken() + ".pdf");
                Files.write(tmpPdf.toPath(), pdfEntry.data);
                double tableY = pageH; // fallback
                try {
                    FormatExtractor extractor = new FormatExtractor();
                    PdfFormat fmt = extractor.extract(tmpPdf.getAbsolutePath());
                    tableY = fmt.tableY;
                } catch (Exception ignored) {}
                tmpPdf.delete();

                double autoWhiteout = Math.max(48, Math.min(tableY - 3, pageH * 0.55));
                double autoFooterY = Math.max(0, pageH - 22.0);

                String imgName = renderAndCachePng(pdfEntry.data, "cfg_preview", PdfPreviewUtil.ZOOM_PREVIEW);
                if (imgName == null) {
                    sendJsonError(exchange, 500, "预览图生成失败");
                    return;
                }

                String json = "{\"success\":true,\"imageUrl\":\"/preview-img/" + imgName + "\","
                        + "\"pageWidth\":" + Math.round(pageW * 10) / 10.0 + ","
                        + "\"pageHeight\":" + Math.round(pageH * 10) / 10.0 + ","
                        + "\"tableY\":" + Math.round(tableY * 10) / 10.0 + ","
                        + "\"autoWhiteoutBottom\":" + Math.round(autoWhiteout * 10) / 10.0 + ","
                        + "\"autoFooterY\":" + Math.round(autoFooterY * 10) / 10.0 + "}";
                sendJson(exchange, json);
            } catch (Exception e) {
                sendJsonError(exchange, 500, "预览失败: " + e.getMessage());
            }
        }

        private String extractSubJson(String json, String key) {
            String pattern = "\"" + key + "\":";
            int idx = json.indexOf(pattern);
            if (idx < 0) return null;
            int brace = json.indexOf('{', idx + pattern.length());
            if (brace < 0) return null;
            int depth = 0, end = brace;
            for (int i = brace; i < json.length(); i++) {
                if (json.charAt(i) == '{') depth++;
                else if (json.charAt(i) == '}') { depth--; if (depth == 0) { end = i; break; } }
            }
            return json.substring(brace, end + 1);
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

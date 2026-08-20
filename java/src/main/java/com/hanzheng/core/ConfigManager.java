package com.hanzheng.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 配置管理器 — 与 Python web_form_server.py 的 configs/ 目录完全对齐。
 *
 * 配置以 JSON 文件存储，结构与 Python 版一致：
 * {
 *   "name": "配置名",
 *   "body_text": "正文",
 *   "contacts": {
 *     "项目联系人": "...", "项目联系人电话": "...",
 *     "收件人": "...", "收件人电话": "...",
 *     "邮箱": "...", "回函地址": "..."
 *   },
 *   "whiteout_bottom": null 或 数字,
 *   "footer_height": null 或 数字,
 *   "is_default": bool
 * }
 * 默认配置名记录在 configs/_default.txt。
 */
public class ConfigManager {

    private static final File CONFIG_DIR = new File("configs");
    private static final File DEFAULT_FILE = new File("configs/_default.txt");

    // 联系方式字段（与 Python 版 contacts 一致）
    private static final String[] CONTACT_KEYS = {
            "项目联系人", "项目联系人电话", "收件人", "收件人电话", "邮箱", "回函地址"
    };

    public static void ensureDir() {
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }
    }

    // ================================================================
    // 配置 CRUD
    // ================================================================

    /** 列出所有配置文件名（.json，按名称倒序，与 Python 一致） */
    public static List<String> listConfigFiles() {
        ensureDir();
        File[] files = CONFIG_DIR.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (File f : files) names.add(f.getName());
        names.sort(Collections.reverseOrder());
        return names;
    }

    /**
     * 加载配置，返回结构化 Map（与 Python cfg 一致）：
     * keys: name, body_text, contacts(Map), whiteout_bottom(String或null),
     *        footer_height(String或null), is_default(String或null)
     */
    public static Map<String, Object> loadConfig(String filename) {
        ensureDir();
        String safe = filename.endsWith(".json") ? filename : filename + ".json";
        File f = new File(CONFIG_DIR, safe);
        if (!f.exists()) return null;
        try {
            String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            return parseJsonObject(content);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 保存配置（Python 契约：name/body_text/contacts/whiteout_bottom/footer_height/is_default）
     * @return 保存的文件名（如 "xxx.json"）
     */
    public static boolean saveConfig(String name, Map<String, String> contacts,
                                     String bodyText, String whiteoutBottom,
                                     String footerHeight, boolean isDefault) {
        ensureDir();
        File f = new File(CONFIG_DIR, name + ".json");
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"").append(escapeJson(name)).append("\",\n");
        sb.append("  \"body_text\": \"").append(escapeJson(bodyText == null ? "" : bodyText)).append("\",\n");
        sb.append("  \"contacts\": {\n");
        int ci = 0;
        for (String k : CONTACT_KEYS) {
            sb.append("    \"").append(escapeJson(k)).append("\": \"")
              .append(escapeJson(contacts == null ? "" : (contacts.get(k) == null ? "" : contacts.get(k)))).append("\"");
            if (ci < CONTACT_KEYS.length - 1) sb.append(",");
            sb.append("\n");
            ci++;
        }
        sb.append("  },\n");
        sb.append("  \"whiteout_bottom\": ").append(whiteoutBottom == null || whiteoutBottom.isEmpty() ? "null" : escapeJson(whiteoutBottom)).append(",\n");
        sb.append("  \"footer_height\": ").append(footerHeight == null || footerHeight.isEmpty() ? "null" : escapeJson(footerHeight)).append("\n");
        sb.append("}\n");
        try {
            Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** 删除配置 */
    public static boolean deleteConfig(String filename) {
        ensureDir();
        String safe = filename.endsWith(".json") ? filename : filename + ".json";
        File f = new File(CONFIG_DIR, safe);
        if (!f.exists()) return false;
        return f.delete();
    }

    // ================================================================
    // 默认配置
    // ================================================================

    /** 获取默认配置名 */
    public static String getDefaultConfigName() {
        ensureDir();
        if (!DEFAULT_FILE.exists()) return "";
        try {
            return new String(Files.readAllBytes(DEFAULT_FILE.toPath()), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    /** 加载默认配置（含 config_name 标记） */
    public static Map<String, Object> loadDefaultConfig() {
        String name = getDefaultConfigName();
        if (name.isEmpty()) return null;
        Map<String, Object> cfg = loadConfig(name);
        if (cfg == null) return null;
        cfg.put("config_name", name);
        return cfg;
    }

    /** 设置默认配置名 */
    public static boolean setDefaultConfig(String name) {
        ensureDir();
        try {
            Files.write(DEFAULT_FILE.toPath(), name.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** 清除默认配置（当保存非默认且当前默认正是该配置时） */
    public static boolean clearDefaultConfig(String name) {
        ensureDir();
        String current = getDefaultConfigName();
        if (current.isEmpty()) return true;
        if (current.equals(name)) {
            return DEFAULT_FILE.delete();
        }
        return true;
    }

    // ================================================================
    // JSON 解析/生成（无第三方依赖，支持一层嵌套）
    // ================================================================

    /**
     * 解析 JSON 对象为 Map<String,Object>。
     * 支持标量值（"string"、数字、true/false、null）和一层嵌套对象。
     */
    public static Map<String, Object> parseJsonObject(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        json = json.trim();
        if (json.isEmpty()) return map;

        int i = 0;
        while (i < json.length()) {
            // 找 key
            int keyStart = json.indexOf('"', i);
            if (keyStart < 0) break;
            int keyEnd = json.indexOf('"', keyStart + 1);
            if (keyEnd < 0) break;
            String key = json.substring(keyStart + 1, keyEnd);

            int colon = json.indexOf(':', keyEnd + 1);
            if (colon < 0) break;

            // 跳过空白
            int vs = colon + 1;
            while (vs < json.length() && (json.charAt(vs) == ' ' || json.charAt(vs) == '\n' || json.charAt(vs) == '\r' || json.charAt(vs) == '\t')) vs++;

            if (vs < json.length() && json.charAt(vs) == '{') {
                // 嵌套对象
                int depth = 0;
                int end = vs;
                for (int j = vs; j < json.length(); j++) {
                    if (json.charAt(j) == '{') depth++;
                    else if (json.charAt(j) == '}') { depth--; if (depth == 0) { end = j; break; } }
                }
                map.put(key, parseJsonObject(json.substring(vs, end + 1)));
                i = end + 1;
            } else if (vs < json.length() && json.charAt(vs) == '"') {
                int ve = json.indexOf('"', vs + 1);
                if (ve < 0) break;
                map.put(key, json.substring(vs + 1, ve));
                i = ve + 1;
            } else {
                // 数字 / true / false / null
                int ve = json.indexOf(',', vs);
                int ve2 = json.indexOf('}', vs);
                if (ve2 >= 0 && (ve < 0 || ve2 < ve)) ve = ve2;
                if (ve < 0) ve = json.length();
                String raw = json.substring(vs, ve).trim();
                if ("null".equals(raw)) {
                    map.put(key, null);
                } else if ("true".equals(raw)) {
                    map.put(key, Boolean.TRUE);
                } else if ("false".equals(raw)) {
                    map.put(key, Boolean.FALSE);
                } else {
                    // 数字
                    try {
                        if (raw.contains(".")) {
                            map.put(key, Double.parseDouble(raw));
                        } else {
                            try {
                                map.put(key, Long.parseLong(raw));
                            } catch (Exception ex) {
                                map.put(key, Double.parseDouble(raw));
                            }
                        }
                    } catch (Exception ex) {
                        map.put(key, raw);
                    }
                }
                i = ve + 1;
            }
            // 跳过逗号/空白
            while (i < json.length() && (json.charAt(i) == ',' || json.charAt(i) == ' ' || json.charAt(i) == '\n' || json.charAt(i) == '\r')) i++;
        }
        return map;
    }

    /** JSON 对象转字符串 */
    public static String toJsonString(Map<String, Object> obj) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : obj.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v instanceof Map) {
                sb.append(toJsonString((Map<String, Object>) v));
            } else if (v == null) {
                sb.append("null");
            } else if (v instanceof Number) {
                sb.append(v);
            } else if (v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append("\"").append(escapeJson(String.valueOf(v))).append("\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

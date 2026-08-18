package com.hanzheng.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 配置管理器 — 对应 Python web_form_server.py 中的 configs/ 目录操作。
 *
 * 配置以 JSON 文件形式存储在 configs/ 目录下，每个文件代表一组填写模板。
 * 使用简单的 JSON 格式读写（无第三方依赖，纯字符串拼接/解析）。
 */
public class ConfigManager {

    private static final File CONFIG_DIR = new File("configs");
    private static final File DEFAULT_FILE = new File("configs/_default.txt");

    /**
     * 确保 configs 目录存在
     */
    public static void ensureDir() {
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }
    }

    // ================================================================
    // 配置 CRUD
    // ================================================================

    /**
     * 列出所有配置名称（去掉 .json 后缀）
     */
    public static List<String> listConfigs() {
        ensureDir();
        File[] files = CONFIG_DIR.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return Collections.emptyList();
        return Arrays.stream(files)
                .map(f -> f.getName().replace(".json", ""))
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 加载指定名称的配置
     */
    public static Map<String, String> loadConfig(String name) {
        ensureDir();
        File f = new File(CONFIG_DIR, name + ".json");
        if (!f.exists()) return null;
        try {
            return parseSimpleJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 保存配置
     */
    public static boolean saveConfig(String name, Map<String, String> fields) {
        ensureDir();
        File f = new File(CONFIG_DIR, name + ".json");
        try {
            Files.write(f.toPath(), toSimpleJson(fields).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 删除配置
     */
    public static boolean deleteConfig(String name) {
        ensureDir();
        File f = new File(CONFIG_DIR, name + ".json");
        if (!f.exists()) return false;
        return f.delete();
    }

    // ================================================================
    // 默认配置
    // ================================================================

    /**
     * 获取默认配置名
     */
    public static String getDefaultConfigName() {
        ensureDir();
        if (!DEFAULT_FILE.exists()) return "";
        try {
            String name = new String(Files.readAllBytes(DEFAULT_FILE.toPath()), StandardCharsets.UTF_8).trim();
            return name;
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * 获取默认配置内容
     */
    public static Map<String, String> loadDefaultConfig() {
        String name = getDefaultConfigName();
        if (name.isEmpty()) return emptyConfig();
        Map<String, String> cfg = loadConfig(name);
        if (cfg == null) return emptyConfig();
        cfg.put("config_name", name);
        return cfg;
    }

    /**
     * 设置默认配置名
     */
    public static boolean setDefaultConfig(String name) {
        ensureDir();
        try {
            Files.write(DEFAULT_FILE.toPath(), name.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ================================================================
    // 简单 JSON 解析/生成（无第三方依赖）
    // ================================================================

    /**
     * 解析简单 JSON 对象 { "key": "value", ... }
     * 不支持嵌套、数组、转义字符
     */
    private static Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        json = json.trim();
        if (!json.startsWith("{")) {
            // 尝试旧版 template 格式（key=value 每行）
            for (String line : json.split("\\r?\\n")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq > 0) {
                    map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            }
            return map;
        }

        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) return map;

        // 按引号对解析 key: "value"
        int i = 0;
        while (i < json.length()) {
            // 找 key
            int keyStart = json.indexOf('"', i);
            if (keyStart < 0) break;
            int keyEnd = json.indexOf('"', keyStart + 1);
            if (keyEnd < 0) break;
            String key = json.substring(keyStart + 1, keyEnd);

            // 找 :
            int colon = json.indexOf(':', keyEnd + 1);
            if (colon < 0) break;

            // 找 value
            int valStart = json.indexOf('"', colon + 1);
            if (valStart < 0) break;
            int valEnd = json.indexOf('"', valStart + 1);
            if (valEnd < 0) break;
            String value = json.substring(valStart + 1, valEnd);

            map.put(key, value);
            i = valEnd + 1;
        }
        return map;
    }

    /**
     * 生成简单 JSON 对象字符串
     */
    public static String toSimpleJson(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        boolean first = true;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (e.getKey().equals("config_name")) continue; // 不存 config_name
            if (!first) sb.append(",\n");
            sb.append("  \"").append(escapeJson(e.getKey())).append("\": ");
            sb.append("\"").append(escapeJson(e.getValue())).append("\"");
            first = false;
        }
        sb.append("\n}\n");
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

    private static Map<String, String> emptyConfig() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("company_name", "");
        m.put("body_text", "");
        m.put("contact_person", "");
        m.put("contact_phone", "");
        m.put("recipient", "");
        m.put("recipient_phone", "");
        m.put("email", "");
        m.put("whiteout_bottom", "280");
        return m;
    }
}

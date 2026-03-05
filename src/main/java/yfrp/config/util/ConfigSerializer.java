package yfrp.config.util;

import yfrp.config.entry.ConfigEntry;
import yfrp.config.format.ConfigFormat;
import yfrp.config.type.ConfigDateTime;
import yfrp.config.type.ConfigDuration;

import java.util.*;

/**
 * 将配置数据序列化为 JSON5 或 YAML 文本。<br>
 * Serializes config data to JSON5 or YAML text.
 * <p>
 * 保存规则：<br>
 * 1. 有注释 → 在配置项前（同缩进）写注释<br>
 * 2. noReload → 在注释后用中英双语各一行标注<br>
 * 3. 有 min → 同行双语标注 min: xxx<br>
 * 4. 有 max → 同行双语标注 max: xxx<br>
 * 5. 日期/时间类型列表值对齐各项
 */
public final class ConfigSerializer {

    private static final String NO_RELOAD_CN = "[无法重载] 此项仅在首次加载时生效，重载时将被忽略。";
    private static final String NO_RELOAD_EN = "[No-Reload] This entry only takes effect on first load and will be ignored on reload.";

    private static final String MIN = "最小值 / Min: ";
    private static final String MAX = "最大值 / Max: ";

    private static final String JSON5_COMMENTS = "// ";
    private static final String YAML_COMMENTS  = "# ";

    private ConfigSerializer() {
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Public entry point
    // ══════════════════════════════════════════════════════════════════════════

    public static String serialize(Map<String, Object> flatValues,
                                   Collection<ConfigEntry<?>> entries,
                                   ConfigFormat format)
    {
        // Build nested map preserving insertion order
        Map<String, Object> nested = buildNestedMap(flatValues);
        // Build a metadata map: id -> entry
        Map<String, ConfigEntry<?>> entryMap = new LinkedHashMap<>();
        for (ConfigEntry<?> e : entries) {
            entryMap.put(e.getId(), e);
        }

        return switch (format) {
            case JSON5 -> serializeJson5(nested, entryMap, 0, "");
            case YAML -> serializeYaml(nested, entryMap, 0, "");
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Nested map builder
    // ══════════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildNestedMap(Map<String, Object> flat) {
        Map<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : flat.entrySet()) {
            String[]            parts = e.getKey().split("\\.", -1);
            Map<String, Object> cur   = root;
            for (int i = 0; i < parts.length - 1; i++) {
                cur = (Map<String, Object>) cur.computeIfAbsent(parts[i], k -> new LinkedHashMap<>());
            }
            cur.put(parts[parts.length - 1], e.getValue());
        }
        return root;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JSON5 serializer
    // ══════════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private static String serializeJson5(Map<String, Object> node,
                                         Map<String, ConfigEntry<?>> entryMap,
                                         int depth,
                                         String pathPrefix)
    {
        String        indent      = "  ".repeat(depth);
        String        childIndent = "  ".repeat(depth + 1);
        StringBuilder sb          = new StringBuilder();
        sb.append("{\n");

        List<Map.Entry<String, Object>> entries = new ArrayList<>(node.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Object> kv       = entries.get(i);
            String                    key      = kv.getKey();
            Object                    val      = kv.getValue();
            String                    fullPath = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;
            ConfigEntry<?>            meta     = entryMap.get(fullPath);
            boolean                   isLast   = (i == entries.size() - 1);

            if (val instanceof Map<?, ?> nested) {
                // write comments for nested objects if any entry under this path has them
                sb.append(childIndent).append(jsonKey(key)).append(": ");
                sb.append(serializeJson5((Map<String, Object>) nested, entryMap, depth + 1, fullPath));
                sb.append(isLast ? "\n" : ",\n");
            } else {
                // Write comment block
                if (meta != null) {
                    writeJson5Comments(sb, meta, childIndent);
                }
                // Write value line
                String valueLine = jsonKey(key) + ": " + toJson5Value(val);
                sb.append(childIndent).append(valueLine);
                if (!isLast) {
                    sb.append(",");
                }
                sb.append("\n");
            }
        }
        sb.append(indent).append("}");
        return sb.toString();
    }

    private static void writeJson5Comments(StringBuilder sb,
                                           ConfigEntry<?> meta,
                                           String indent)
    {
        // 1. 用户注释
        if (meta.getComment() != null) {
            for (String line : meta.getComment().split("\n", -1)) {
                sb.append(indent).append(JSON5_COMMENTS).append(line).append("\n");
            }
        }
        // 2. noReload 双语标注
        if (meta.isNoReload()) {
            sb.append(indent).append(JSON5_COMMENTS).append(NO_RELOAD_CN).append("\n");
            sb.append(indent).append(JSON5_COMMENTS).append(NO_RELOAD_EN).append("\n");
        }
        // 3. 范围标注
        if (meta.hasRange()) {
            if (meta.getMinValue() != null) {
                sb.append(indent).append(JSON5_COMMENTS).append(MIN).append(meta.getMinValue()).append("\n");
            }
            if (meta.getMaxValue() != null) {
                sb.append(indent).append(JSON5_COMMENTS).append(MAX).append(meta.getMaxValue()).append("\n");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static String toJson5Value(Object val) {
        if (val == null) {
            return "null";
        }
        if (val instanceof String s) {
            return "\"" + escapeJson(s) + "\"";
        }
        if (val instanceof Boolean || val instanceof Number) {
            return val.toString();
        }
        if (val instanceof ConfigDuration || val instanceof ConfigDateTime) {
            return "\"" + val + "\"";
        }
        if (val instanceof List<?> list) {
            return buildJson5List((List<Object>) list);
        }
        return "\"" + escapeJson(val.toString()) + "\"";
    }

    private static String buildJson5List(List<Object> list) {
        if (list.isEmpty()) {
            return "[]";
        }
        // Check if alignment needed (Duration or DateTime)
        boolean alignable = list.get(0) instanceof ConfigDuration || list.get(0) instanceof ConfigDateTime;
        if (alignable) {
            List<String>  strs   = list.stream().map(Object::toString).toList();
            int           maxLen = strs.stream().mapToInt(String::length).max().orElse(0);
            StringBuilder sb     = new StringBuilder("[");
            for (int i = 0; i < strs.size(); i++) {
                String str = strs.get(i);
                // Pad value string to maxLen for alignment
                String padded = String.format("%-" + maxLen + "s", str);
                sb.append("\"").append(padded).append("\"");
                if (i < strs.size() - 1) {
                    sb.append(", ");
                }
            }
            sb.append("]");
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(toJson5Value(list.get(i)));
            if (i < list.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static String jsonKey(String key) {
        // Use quoted keys for safety
        return "\"" + escapeJson(key) + "\"";
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // YAML serializer
    // ══════════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private static String serializeYaml(Map<String, Object> node,
                                        Map<String, ConfigEntry<?>> entryMap,
                                        int depth,
                                        String pathPrefix)
    {
        String        indent = "  ".repeat(depth);
        StringBuilder sb     = new StringBuilder();

        for (Map.Entry<String, Object> kv : node.entrySet()) {
            String         key      = kv.getKey();
            Object         val      = kv.getValue();
            String         fullPath = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;
            ConfigEntry<?> meta     = entryMap.get(fullPath);

            if (val instanceof Map<?, ?> nested) {
                sb.append(indent).append(yamlKey(key)).append(":\n");
                sb.append(serializeYaml((Map<String, Object>) nested, entryMap, depth + 1, fullPath));
            } else {
                // Write comment block
                if (meta != null) {
                    writeYamlComments(sb, meta, indent);
                }
                // Write value line
                sb.append(indent).append(yamlKey(key)).append(": ");
                sb.append(toYamlValue(val));
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private static void writeYamlComments(StringBuilder sb,
                                          ConfigEntry<?> meta,
                                          String indent)
    {
        // 1. 用户注释
        if (meta.getComment() != null) {
            for (String line : meta.getComment().split("\n", -1)) {
                sb.append(indent).append("# ").append(line).append("\n");
            }
        }
        // 2. noReload 双语标注
        if (meta.isNoReload()) {
            sb.append(indent).append(YAML_COMMENTS).append(NO_RELOAD_CN).append("\n");
            sb.append(indent).append(YAML_COMMENTS).append(NO_RELOAD_EN).append("\n");
        }
        // 3. 范围标注
        if (meta.hasRange()) {
            if (meta.getMinValue() != null) {
                sb.append(indent).append(YAML_COMMENTS).append(MIN).append(meta.getMinValue()).append("\n");
            }
            if (meta.getMaxValue() != null) {
                sb.append(indent).append(YAML_COMMENTS).append(MAX).append(meta.getMaxValue()).append("\n");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static String toYamlValue(Object val) {
        if (val == null) {
            return "null";
        }
        if (val instanceof String s) {
            return yamlStringValue(s);
        }
        if (val instanceof Boolean) {
            return val.toString();
        }
        if (val instanceof Number) {
            return val.toString();
        }
        if (val instanceof ConfigDuration || val instanceof ConfigDateTime) {
            return "\"" + val + "\"";
        }
        if (val instanceof List<?> list) {
            return buildYamlInlineList((List<Object>) list);
        }
        return yamlStringValue(val.toString());
    }

    private static String buildYamlInlineList(List<Object> list) {
        if (list.isEmpty()) {
            return "[]";
        }
        boolean alignable = list.get(0) instanceof ConfigDuration || list.get(0) instanceof ConfigDateTime;
        if (alignable) {
            List<String>  strs   = list.stream().map(Object::toString).toList();
            int           maxLen = strs.stream().mapToInt(String::length).max().orElse(0);
            StringBuilder sb     = new StringBuilder("[");
            for (int i = 0; i < strs.size(); i++) {
                String padded = String.format("%-" + maxLen + "s", strs.get(i));
                sb.append("\"").append(padded).append("\"");
                if (i < strs.size() - 1) {
                    sb.append(", ");
                }
            }
            sb.append("]");
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(toYamlValue(list.get(i)));
            if (i < list.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static String yamlStringValue(String s) {
        // Quote if contains special chars
        if (s.isEmpty() || s.contains(":") || s.contains("#") || s.contains("\"") ||
            s.contains("'") || s.contains("\n") || s.startsWith(" ") || s.endsWith(" ") ||
            s.equals("true") || s.equals("false") || s.equals("null")) {
            return "\"" + escapeJson(s) + "\"";
        }
        return s;
    }

    private static String yamlKey(String key) {
        if (key.contains(":") || key.contains("#") || key.contains(" ")) {
            return "\"" + escapeJson(key) + "\"";
        }
        return key;
    }
}

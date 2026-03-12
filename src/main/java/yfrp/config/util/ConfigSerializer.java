package yfrp.config.util;

import yfrp.config.entry.ConfigEntry;
import yfrp.config.format.ConfigFormat;
import yfrp.config.type.ConfigDateTime;
import yfrp.config.type.ConfigDuration;
import yfrp.config.type.ConfigValueType;

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

    private static final String INDENT = "  ";

    private static final String JSON5_COMMENTS = "// ";
    private static final String YAML_COMMENTS  = "# ";

    private static final String NO_RELOAD_CN = "[无法重载] 此项仅在首次加载时生效，重载时将被忽略。";
    private static final String NO_RELOAD_EN = "[No-Reload] This entry only takes effect on first load and will be ignored on reload.";

    private static final String MIN = "最小值 / Min: ";
    private static final String MAX = "最大值 / Max: ";

    private static final String ENCODING = "-*- coding: utf-8 -*-";

    private static final String YAML_QUOTE = "'";

    /**
     * YAML plain scalar safety rules.
     * Values matching these rules should be quoted to avoid implicit type conversion
     * or syntax conflicts.
     */
    private static final Set<String> YAML_AMBIGUOUS_SCALARS = Set.of(
            // boolean true
            "true", "True", "TRUE",
            "yes", "Yes", "YES",
            "on", "On", "ON",

            // boolean false
            "false", "False", "FALSE",
            "no", "No", "NO",
            "off", "Off", "OFF",

            // null
            "null", "Null", "NULL",
            "~"
    );

    /**
     * Characters that make a scalar unsafe when appearing at the beginning.
     */
    private static final Set<Character> YAML_PLAIN_SCALAR_DISALLOWED_START = Set.of(
            '-', '?', ':',
            ',', '[', ']',
            '{', '}',
            '&', '*',
            '!', '|', '>',
            '\'', '"',
            '%', '@', '`',
            ' '        // leading space
    );

    /**
     * Characters that make a scalar unsafe when appearing at the end.
     */
    private static final Set<Character> YAML_PLAIN_SCALAR_DISALLOWED_END = Set.of(
            ' '        // trailing space
    );

    /**
     * Characters that cannot safely appear inside an unquoted plain scalar.
     */
    private static final Set<Character> YAML_PLAIN_SCALAR_SPECIAL_CHARS = Set.of(
            ':',       // key separator
            '#',       // comment

            '[', ']',  // flow sequence
            '{', '}',  // flow mapping

            ','        // flow separator
    );

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

        var raw = switch (format) {
            case JSON5 -> JSON5_COMMENTS + ENCODING + "\n"
                          + serializeJson5(nested, entryMap, 0, "");
            case YAML -> YAML_COMMENTS + ENCODING + "\n"
                         + serializeYaml(nested, entryMap, 0, "", false);
        };

        // 去掉每行末尾的尾随空格
        return trimTrailingSpacesPerLine(raw);
    }

    private static String trimTrailingSpacesPerLine(String s) {
        String[]      lines = s.split("\n", -1);
        StringBuilder sb    = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int    end  = line.length();
            while (end > 0 && line.charAt(end - 1) == ' ') {
                end--;
            }
            sb.append(line, 0, end);
            if (i < lines.length - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
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
        String        indent      = INDENT.repeat(depth);
        String        childIndent = INDENT.repeat(depth + 1);
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
                    writeComments(JSON5_COMMENTS, sb, meta, childIndent);
                }
                // Write value line
                String valueLine = jsonKey(key) + ": " + toJson5Value(val, childIndent);
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

    @SuppressWarnings("unchecked")
    private static String toJson5Value(Object val,
                                       String indent)
    {
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
            return buildJson5List((List<Object>) list, indent);
        }
        if (val instanceof Map<?, ?> map) {
            return buildJson5Map((Map<String, Object>) map, indent);
        }
        return "\"" + escapeJson(val.toString()) + "\"";
    }

    private static String buildJson5List(List<Object> list,
                                         String indent)
    {
        if (list.isEmpty()) {
            return "[]";
        }

        String childIndent = indent + INDENT;
        boolean alignable = list.get(0) instanceof ConfigDuration
                            || list.get(0) instanceof ConfigDateTime;
        StringBuilder sb = new StringBuilder();

        if (alignable) {
            List<String> strs   = list.stream().map(Object::toString).toList();
            int          maxLen = strs.stream().mapToInt(String::length).max().orElse(0);
            sb.append("[\n");
            for (int i = 0; i < strs.size(); i++) {
                String padded = String.format("%-" + maxLen + "s", strs.get(i));
                sb.append(childIndent).append("\"").append(padded).append("\"");
                if (i < strs.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append(indent).append("]");
        } else {
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append(childIndent).append(toJson5Value(list.get(i), childIndent));
                if (i < list.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append(indent).append("]");
        }
        return sb.toString();
    }

    private static String buildJson5Map(Map<String, Object> map,
                                        String indent)
    {
        if (map.isEmpty()) {
            return "{}";
        }

        String                          childIndent = indent + INDENT;
        StringBuilder                   sb          = new StringBuilder("{\n");
        List<Map.Entry<String, Object>> entries     = new ArrayList<>(map.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Object> e = entries.get(i);
            sb.append(childIndent).append(jsonKey(e.getKey())).append(": ")
                    .append(toJson5Value(e.getValue(), childIndent));
            if (i < entries.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(indent).append("}");
        return sb.toString();
    }

    private static String jsonKey(String key) {
        // Use quoted keys for safety
        return "\"" + escapeJson(key) + "\"";
    }

    private static String escapeJson(String s) {
        return normalizeLineEndings(s)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    /**
     * 将 \r\n 和 \r 统一替换为 \n。<br>
     * Normalize all line endings to \n.
     */
    private static String normalizeLineEndings(String s) {
        // \r\n → \n，剩余单独的 \r → \n
        return s.replace("\r\n", "\n").replace("\r", "\n");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // YAML serializer
    // ══════════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private static String serializeYaml(Map<String, Object> node,
                                        Map<String, ConfigEntry<?>> entryMap,
                                        int depth,
                                        String pathPrefix,
                                        boolean insideMapEntry)
    {
        String        indent = INDENT.repeat(depth);
        StringBuilder sb     = new StringBuilder();

        for (Map.Entry<String, Object> kv : node.entrySet()) {
            String         key        = kv.getKey();
            Object         val        = kv.getValue();
            String         fullPath   = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;
            ConfigEntry<?> meta       = insideMapEntry ? null : entryMap.get(fullPath);
            boolean        isMapEntry = meta != null && meta.getValueType() == ConfigValueType.MAP;

            if (val instanceof Map<?, ?> nested) {
                sb.append(indent).append(yamlKey(key)).append(":\n");
                sb.append(serializeYaml(
                        (Map<String, Object>) nested,
                        entryMap,
                        depth + 1,
                        fullPath,
                        isMapEntry || insideMapEntry
                ));
            } else {
                // Write comment block
                if (meta != null) {
                    writeComments(YAML_COMMENTS, sb, meta, indent);
                }
                // Write value line
                sb.append(indent).append(yamlKey(key)).append(": ");
                sb.append(toYamlValue(val, indent));
                sb.append("\n");
            }

            if (!insideMapEntry) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String toYamlValue(Object val,
                                      String indent)
    {
        if (val == null) {
            return "null";
        }
        if (val instanceof String s) {
            return yamlStringValue(s, indent);
        }
        if (val instanceof Boolean) {
            return val.toString();
        }
        if (val instanceof Number) {
            return val.toString();
        }
        if (val instanceof ConfigDuration || val instanceof ConfigDateTime) {
            return YAML_QUOTE + val + YAML_QUOTE;
        }
        if (val instanceof List<?> list) {
            return buildYamlBlockList((List<Object>) list, indent);
        }
        if (val instanceof Map<?, ?> map) {
            return buildYamlInlineMap((Map<String, Object>) map, indent);
        }
        return yamlStringValue(val.toString(), indent);
    }

    private static String buildYamlBlockList(List<Object> list,
                                             String indent)
    {
        if (list.isEmpty()) {
            return "[]";
        }
        boolean alignable = list.get(0) instanceof ConfigDuration
                            || list.get(0) instanceof ConfigDateTime;
        String        childIndent = indent + INDENT;
        StringBuilder sb          = new StringBuilder();
        if (alignable) {
            List<String> strs   = list.stream().map(Object::toString).toList();
            int          maxLen = strs.stream().mapToInt(String::length).max().orElse(0);
            for (String str : strs) {
                String padded = String.format("%-" + maxLen + "s", str);
                sb.append("\n").append(childIndent).append("- '").append(padded).append("'");
            }
        } else {
            for (Object item : list) {
                sb.append("\n").append(childIndent).append("- ")
                        .append(toYamlValue(item, childIndent));
            }
        }
        return sb.toString();
    }

    private static String buildYamlInlineMap(Map<String, Object> map,
                                             String indent)
    {
        if (map.isEmpty()) {
            return "{}";
        }
        String        childIndent = indent + INDENT;
        StringBuilder sb          = new StringBuilder();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sb.append("\n").append(childIndent)
                    .append(yamlKey(entry.getKey())).append(": ")
                    .append(toYamlValue(entry.getValue(), childIndent));
        }
        return sb.toString();
    }

    private static String yamlStringValue(String s,
                                          String indent)
    {
        s = normalizeLineEndings(s);

        // 多行：使用 | 块标量
        if (s.contains("\n")) {
            String        childIndent = indent + INDENT;
            StringBuilder sb          = new StringBuilder("|\n");
            for (String line : s.split("\n", -1)) {
                sb.append(childIndent).append(line).append("\n");
            }
            // 末尾已有 \n，调用处再 append("\n") 会多一行，
            // 所以去掉最后一个 \n，由调用处统一补
            if (sb.charAt(sb.length() - 1) == '\n') {
                sb.deleteCharAt(sb.length() - 1);
            }
            return sb.toString();
        }

        boolean needsQuote = s.isEmpty()
                             || areAsciiDigits(s)
                             || YAML_AMBIGUOUS_SCALARS.contains(s)
                             || YAML_PLAIN_SCALAR_DISALLOWED_START.contains(s.charAt(0))
                             || YAML_PLAIN_SCALAR_DISALLOWED_END.contains(s.charAt(s.length() - 1))
                             || containsAny(s, YAML_PLAIN_SCALAR_SPECIAL_CHARS);
        if (needsQuote) {
            return YAML_QUOTE + s.replace(YAML_QUOTE, YAML_QUOTE.repeat(2)) + YAML_QUOTE;
        }
        return s;
    }

    private static boolean containsAny(String s,
                                       Collection<Character> chars)
    {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            for (char target : chars) {
                if (c == target) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean areAsciiDigits(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static String yamlKey(String key) {
        if (key.contains(":") || key.contains("#") || key.contains(" ")) {
            return YAML_QUOTE + escapeJson(key) + YAML_QUOTE;
        }
        return key;
    }

    private static void writeComments(String commentsPrefix,
                                      StringBuilder sb,
                                      ConfigEntry<?> meta,
                                      String indent)
    {
        List<String> commentList = new ArrayList<>(3);

        if (!meta.getComment().isBlank()) {
            var sb2 = new StringBuilder();

            for (String line : meta.getComment().split("\n", -1)) {
                sb2.append(indent).append(commentsPrefix).append(line).append("\n");
            }

            commentList.add(sb2.toString());
        }

        if (meta.isNoReload()) {
            commentList.add(indent + commentsPrefix + NO_RELOAD_CN + "\n" +
                            indent + commentsPrefix + NO_RELOAD_EN + "\n");
        }

        if (meta.hasRange()) {
            var sb2 = new StringBuilder();

            for (String line : formatRange(meta)) {
                sb2.append(indent).append(commentsPrefix).append(line).append("\n");
            }

            commentList.add(sb2.toString());
        }

        sb.append(String.join("\n", commentList));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Range formatting
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 生成对齐后的 min/max 行列表（0、1 或 2 个元素）。
     * <p>
     * 每个元素是完整的一行文字（不含注释前缀和换行），例如 "最小值 / Min:  10"。
     * <p>
     * Returns 0–2 lines for min/max, with values aligned according to type.
     */
    private static List<String> formatRange(ConfigEntry<?> meta) {
        if (!meta.hasRange() && meta.getMinValue() == null && meta.getMaxValue() == null) {
            return List.of();
        }

        Comparable<Object> minVal = meta.getMinValue();
        Comparable<Object> maxVal = meta.getMaxValue();

        // 只有 max
        if (minVal == null && maxVal != null) {
            return List.of(MAX + maxVal);
        }
        // 只有 min
        if (minVal != null && maxVal == null) {
            return List.of(MIN + minVal);
        }
        // 两者都有
        assert minVal != null;

        Object rawMin = minVal;
        Object rawMax = maxVal;

        // Duration
        if (rawMin instanceof ConfigDuration minD && rawMax instanceof ConfigDuration maxD) {
            return formatDurationRange(minD, maxD);
        }

        // 数字类型：按小数点对齐
        if (rawMin instanceof Number && rawMax instanceof Number) {
            String minStr = rawMin.toString();
            String maxStr = rawMax.toString();

            int minDot = minStr.indexOf('.');
            int maxDot = maxStr.indexOf('.');

            // 整数部分长度（小数点前）
            int minIntLen = minDot >= 0 ? minDot : minStr.length();
            int maxIntLen = maxDot >= 0 ? maxDot : maxStr.length();

            // 小数部分长度（小数点后，含小数点本身；无小数点则为0）
            int minFracLen = minDot >= 0 ? minStr.length() - minDot : 0;
            int maxFracLen = maxDot >= 0 ? maxStr.length() - maxDot : 0;

            int intWidth  = Math.max(minIntLen, maxIntLen);
            int fracWidth = Math.max(minFracLen, maxFracLen);

            return List.of(
                    MIN + alignNumber(minStr, minIntLen, intWidth, fracWidth),
                    MAX + alignNumber(maxStr, maxIntLen, intWidth, fracWidth)
            );
        }

        // DateTime 及其他
        return List.of(
                MIN + minVal,
                MAX + maxVal
        );
    }

    private static String alignNumber(String s,
                                      int ownIntLen,
                                      int intWidth,
                                      int fracWidth)
    {
        String intPart  = s.substring(0, ownIntLen);
        String fracPart = s.substring(ownIntLen); // ".xxx" 或 ""

        String paddedInt = String.format("%" + intWidth + "s", intPart);
        // fracWidth 为 0 说明 min/max 均无小数部分，直接拼空字符串
        String paddedFrac = fracWidth > 0
                            ? String.format("%-" + fracWidth + "s", fracPart)
                            : fracPart;

        return paddedInt + paddedFrac;
    }

    private static List<String> formatDurationRange(ConfigDuration minD,
                                                    ConfigDuration maxD)
    {
        long minDays = minD.getDays(), maxDays = maxD.getDays();
        long minH    = minD.getHours(), maxH = maxD.getHours();
        long minMin  = minD.getMinutes(), maxMin = maxD.getMinutes();
        long minSec  = minD.getSeconds(), maxSec = maxD.getSeconds();

        boolean hasDays = minDays != 0 || maxDays != 0;
        boolean hasH    = hasDays || minH != 0 || maxH != 0;
        boolean hasMin  = hasH || minMin != 0 || maxMin != 0;
        // seconds always present

        int dW   = hasDays ? Math.max(String.valueOf(minDays).length(), String.valueOf(maxDays).length()) : 0;
        int hW   = hasH ? Math.max(String.valueOf(minH).length(), String.valueOf(maxH).length()) : 0;
        int minW = hasMin ? Math.max(String.valueOf(minMin).length(), String.valueOf(maxMin).length()) : 0;
        int sW   = Math.max(String.valueOf(minSec).length(), String.valueOf(maxSec).length());

        return List.of(
                MIN + formatDurationAligned(minDays, minH, minMin, minSec, hasDays, hasH, hasMin, dW, hW, minW, sW),
                MAX + formatDurationAligned(maxDays, maxH, maxMin, maxSec, hasDays, hasH, hasMin, dW, hW, minW, sW)
        );
    }

    private static String formatDurationAligned(long d,
                                                long h,
                                                long min,
                                                long s,
                                                boolean hasDays,
                                                boolean hasH,
                                                boolean hasMin,
                                                int dW,
                                                int hW,
                                                int minW,
                                                int sW)
    {
        StringBuilder sb = new StringBuilder();
        if (hasDays) {
            if (d != 0) {
                sb.append(String.format("%" + dW + "d", d)).append("d ");
            } else {
                sb.append(" ".repeat(dW + "d ".length()));
            }
        }
        if (hasH) {
            if (d != 0 || h != 0) {
                sb.append(String.format("%" + hW + "d", h)).append("h ");
            } else {
                sb.append(" ".repeat(hW + "h ".length()));
            }
        }
        if (hasMin) {
            if (d != 0 || h != 0 || min != 0) {
                sb.append(String.format("%" + minW + "d", min)).append("m ");
            } else {
                sb.append(" ".repeat(minW + "m ".length()));
            }
        }
        sb.append(String.format("%" + sW + "d", s)).append("s");

        return sb.toString();
    }

}

package yfrp.config.util;

import yfrp.config.format.ConfigFormat;
import org.hjson.JsonObject;
import org.hjson.JsonValue;
import org.hjson.JsonArray;
import org.yaml.snakeyaml.Yaml;

import java.io.StringReader;
import java.util.*;

/**
 * 将 JSON5 或 YAML 文本解析为平铺的 {@code Map<String, Object>}。<br>
 * Parses JSON5 or YAML text into a flat {@code Map<String, Object>}.
 * <p>
 * key 格式：用 '.' 连接的完整路径（例如 "server.port"）。<br>
 * Key format: full path joined by '.' (e.g. "server.port").
 */
public final class ConfigParser {

    private ConfigParser() {}

    /**
     * 解析文本，返回平铺 Map。
     */
    public static Map<String, Object> parse(String text, ConfigFormat format) {
        if (text == null || text.isBlank()) return new LinkedHashMap<>();
        return switch (format) {
            case JSON5 -> parseJson5(text);
            case YAML  -> parseYaml(text);
        };
    }

    // ── JSON5 ─────────────────────────────────────────────────────────────────

    private static Map<String, Object> parseJson5(String text) {
        try {
            JsonValue root = JsonValue.readHjson(text);
            Map<String, Object> flat = new LinkedHashMap<>();
            if (root.isObject()) {
                flattenJson(root.asObject(), "", flat);
            }
            return flat;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON5 config: " + e.getMessage(), e);
        }
    }

    private static void flattenJson(JsonObject obj, String prefix, Map<String, Object> flat) {
        for (JsonObject.Member member : obj) {
            String key = prefix.isEmpty() ? member.getName() : prefix + "." + member.getName();
            JsonValue val = member.getValue();
            if (val.isObject()) {
                flattenJson(val.asObject(), key, flat);
            } else if (val.isArray()) {
                flat.put(key, jsonArrayToList(val.asArray()));
            } else {
                flat.put(key, jsonScalar(val));
            }
        }
    }

    private static List<Object> jsonArrayToList(JsonArray arr) {
        List<Object> list = new ArrayList<>();
        for (JsonValue item : arr) {
            if (item.isArray()) {
                list.add(jsonArrayToList(item.asArray()));
            } else if (item.isObject()) {
                // nested objects in arrays: skip or stringify
                list.add(item.toString());
            } else {
                list.add(jsonScalar(item));
            }
        }
        return list;
    }

    private static Object jsonScalar(JsonValue val) {
        if (val.isNull())    return null;
        if (val.isBoolean()) return val.asBoolean();
        if (val.isNumber())  {
            double d = val.asDouble();
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < Long.MAX_VALUE) {
                return (long) d;
            }
            return d;
        }
        if (val.isString())  return val.asString();
        return val.toString();
    }

    // ── YAML ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseYaml(String text) {
        try {
            Yaml yaml = new Yaml();
            Object parsed = yaml.load(new StringReader(text));
            if (parsed == null) return new LinkedHashMap<>();
            if (!(parsed instanceof Map<?, ?> map)) {
                throw new RuntimeException("YAML root must be a mapping");
            }
            Map<String, Object> flat = new LinkedHashMap<>();
            flattenYaml((Map<String, Object>) map, "", flat);
            return flat;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse YAML config: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void flattenYaml(Map<String, Object> map, String prefix, Map<String, Object> flat) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object val = entry.getValue();
            if (val instanceof Map<?, ?> nested) {
                flattenYaml((Map<String, Object>) nested, key, flat);
            } else if (val instanceof List<?> list) {
                flat.put(key, normalizeList(list));
            } else {
                flat.put(key, val);
            }
        }
    }

    private static List<Object> normalizeList(List<?> list) {
        List<Object> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?>) {
                result.add(item.toString());
            } else {
                result.add(item);
            }
        }
        return result;
    }
}

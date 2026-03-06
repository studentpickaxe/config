package yfrp.config.util;

import org.jetbrains.annotations.Nullable;
import yfrp.config.core.ConfigEventHandler;
import yfrp.config.type.ConfigDateTime;
import yfrp.config.type.ConfigDuration;
import yfrp.config.type.ConfigValueType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 配置值类型转换工具。<br>
 * Utility for converting raw config values to typed values.
 * <p>
 * 转换规则：<br>
 * - STRING：强制 toString<br>
 * - 数字类型：不是数字则丢弃（返回 null），否则强转<br>
 * - BOOLEAN：支持 true/false/1/0/"true"/"false"<br>
 * - DURATION / DATETIME：尝试解析字符串<br>
 * - LIST_*：对每个元素单独转换，转换失败的元素丢弃
 */
public record ValueConverter(
        @Nullable ConfigEventHandler handler
)
{

    /**
     * 将 rawValue 转换为 type 对应的 Java 类型。<br>
     * Convert rawValue to the Java type corresponding to {@code type}.
     *
     * @param raw  原始值（来自解析器）/ raw value from parser
     * @param type 目标类型 / target type
     * @return 转换后的值，转换失败返回 null / converted value, null if conversion fails
     */
    @Nullable
    public Object convert(@Nullable Object raw,
                          ConfigValueType type)
    {
        if (raw == null) {
            return null;
        }
        return switch (type) {
            case STRING -> raw.toString();
            case LONG -> toLong(raw);
            case INT -> toInt(raw);
            case DOUBLE -> toDouble(raw);
            case DURATION -> toDuration(raw);
            case DATETIME -> toDateTime(raw);
            case BOOLEAN -> toBoolean(raw);
            case LIST_STRING -> toList(raw, ConfigValueType.STRING);
            case LIST_LONG -> toList(raw, ConfigValueType.LONG);
            case LIST_INT -> toList(raw, ConfigValueType.INT);
            case LIST_DOUBLE -> toList(raw, ConfigValueType.DOUBLE);
            case LIST_DURATION -> toList(raw, ConfigValueType.DURATION);
            case LIST_DATETIME -> toList(raw, ConfigValueType.DATETIME);
            case LIST_BOOLEAN -> toList(raw, ConfigValueType.BOOLEAN);
            case MAP -> toMap(raw);
        };
    }

    // ── Scalar converters ─────────────────────────────────────────────────────

    @Nullable
    public Long toLong(@Nullable Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(raw.toString().trim());
        } catch (NumberFormatException e) {
            if (handler != null) {
                handler.onConversionFailed("LONG", raw);
            }
            return null;
        }
    }

    @Nullable
    public Integer toInt(@Nullable Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            if (handler != null) {
                handler.onConversionFailed("INT", raw);
            }
            return null;
        }
    }

    @Nullable
    public Double toDouble(@Nullable Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(raw.toString().trim());
        } catch (NumberFormatException e) {
            if (handler != null) {
                handler.onConversionFailed("DOUBLE", raw);
            }
            return null;
        }
    }

    @Nullable
    public Boolean toBoolean(@Nullable Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        String s = raw.toString().trim().toLowerCase();
        if (s.equals("true") || s.equals("1")) {
            return true;
        }
        if (s.equals("false") || s.equals("0")) {
            return false;
        }
        if (handler != null) {
            handler.onConversionFailed("BOOLEAN", raw);
        }
        return null;
    }

    @Nullable
    public ConfigDuration toDuration(@Nullable Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof ConfigDuration d) {
            return d;
        }
        try {
            return ConfigDuration.parse(raw.toString());
        } catch (Exception e) {
            if (handler != null) {
                handler.onConversionFailed("DURATION", raw);
            }
            return null;
        }
    }

    @Nullable
    public ConfigDateTime toDateTime(@Nullable Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof ConfigDateTime dt) {
            return dt;
        }
        try {
            return ConfigDateTime.parse(raw.toString());
        } catch (Exception e) {
            if (handler != null) {
                handler.onConversionFailed("DATETIME", raw);
            }
            return null;
        }
    }

    // ── Map converter ─────────────────────────────────────────────────────────

    @Nullable
    @SuppressWarnings("unchecked")
    public Map<String, Object> toMap(@Nullable Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        if (handler != null) {
            handler.onConversionFailed("MAP", raw);
        }
        return null;
    }

    // ── List converter ────────────────────────────────────────────────────────

    @Nullable
    public List<Object> toList(@Nullable Object raw,
                               ConfigValueType elementType)
    {
        if (raw == null) {
            return null;
        }
        Collection<?> col;
        if (raw instanceof Collection<?> c) {
            col = c;
        } else {
            // single value → wrap in list
            col = List.of(raw);
        }
        List<Object> result = new ArrayList<>();
        for (Object item : col) {
            Object converted = convert(item, elementType);
            if (converted != null) {
                result.add(converted);
            } else {
                if (handler != null) {
                    handler.onListElementConversionFailed(elementType.name().toUpperCase(), item);
                }
            }
        }
        return result;
    }

    // ── Range check ───────────────────────────────────────────────────────────

    /**
     * 检查值是否在 [min, max] 范围内（null 表示无限制）。<br>
     * Check if value is within [min, max] range (null means unlimited).
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean inRange(@Nullable Object value,
                           @Nullable Comparable min,
                           @Nullable Comparable max)
    {
        if (value == null) {
            return true;
        }
        if (!(value instanceof Comparable v)) {
            return true; // non-comparable types skip check
        }
        return (min == null || v.compareTo(min) >= 0) &&
               (max == null || v.compareTo(max) <= 0);
    }
}

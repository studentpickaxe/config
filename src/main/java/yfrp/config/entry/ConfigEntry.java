package yfrp.config.entry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import yfrp.config.type.ConfigDateTime;
import yfrp.config.type.ConfigDuration;
import yfrp.config.type.ConfigValueType;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 配置项定义（不可变）。<br>
 * Immutable configuration entry definition.
 * <p>
 * 泛型 {@code T} 为该配置项的值类型（基本类型、ConfigDuration、ConfigDateTime 或其 List）。<br>
 * Generic {@code T} is the value type of this entry.
 *
 * @param <T> 值类型 / value type
 */
public final class ConfigEntry<T> {

    /**
     * 合法 id 段正则：非空、非纯空格
     */
    private static final Pattern SEGMENT_PATTERN = Pattern.compile("^(?!\\s*$).+$");

    private final @NotNull  String             id;
    private final @NotNull  String             comment;
    private final           boolean            noReload;
    private final           T                  defaultValue;
    private final           ConfigValueType    valueType;
    private final @Nullable Comparable<Object> minValue;
    private final @Nullable Comparable<Object> maxValue;

    @SuppressWarnings("unchecked")
    private ConfigEntry(Builder<T> builder) {
        this.id = validateId(builder.id);
        this.comment = builder.commentBuilder.toString();
        this.noReload = builder.noReload;
        this.defaultValue = Objects.requireNonNull(builder.defaultValue, "defaultValue must not be null");
        this.valueType = Objects.requireNonNull(builder.valueType, "valueType must not be null");
        this.minValue = (Comparable<Object>) builder.minValue;
        this.maxValue = (Comparable<Object>) builder.maxValue;
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private static String validateId(@Nullable String id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        String[] parts = id.split("\\.", -1);
        if (parts.length == 0) {
            throw new IllegalArgumentException("id must not be empty");
        }
        for (String part : parts) {
            if (part.isEmpty()) {
                throw new IllegalArgumentException(
                        "id '" + id + "' contains empty segment (e.g. leading/trailing/double dot)");
            }
            if (!SEGMENT_PATTERN.matcher(part).matches()) {
                throw new IllegalArgumentException(
                        "id segment '" + part + "' in id '" + id + "' must not be blank or whitespace-only");
            }
        }
        if ("version".equals(id)) {
            throw new IllegalArgumentException(
                    "id 'version' is reserved and cannot be used as a config entry id");
        }
        return id;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /**
     * 配置项 id，用 '.' 分隔层级<br>
     * Entry id, '.' separates nesting levels
     */
    @NotNull
    public String getId() {
        return id;
    }

    /**
     * 注释，null 表示无<br>
     * Comment, null means none
     */
    @NotNull
    public String getComment() {
        return comment;
    }

    /**
     * 是否无法重载<br>
     * Whether this entry cannot be reloaded after first load
     */
    public boolean isNoReload() {
        return noReload;
    }

    /**
     * 默认值<br>
     * Default value
     */
    @NotNull
    public T getDefaultValue() {
        return defaultValue;
    }

    /**
     * 值类型<br>
     * Value type
     */
    @NotNull
    public ConfigValueType getValueType() {
        return valueType;
    }

    /**
     * 最小值（含），null 表示无限制<br>
     * Minimum value (inclusive), null = no limit
     */
    @Nullable
    public Comparable<Object> getMinValue() {
        return minValue;
    }

    /**
     * 最大值（含），null 表示无限制<br>
     * Maximum value (inclusive), null = no limit
     */
    @Nullable
    public Comparable<Object> getMaxValue() {
        return maxValue;
    }

    /**
     * 是否定义了范围约束<br>
     * Whether range constraints are defined
     */
    public boolean hasRange() {
        return minValue != null || maxValue != null;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static <T> Builder<T> builder(@NotNull String id,
                                         @NotNull ConfigValueType valueType,
                                         @NotNull T defaultValue,
                                         boolean noReload)
    {
        return new Builder<>(id, valueType, defaultValue, noReload);
    }

    public static final class Builder<T> {
        private final @NotNull String          id;
        private final @NotNull ConfigValueType valueType;
        private final @NotNull T               defaultValue;
        private final          boolean         noReload;
        private final @NotNull StringBuilder   commentBuilder = new StringBuilder();
        private @Nullable      Object          minValue       = null;
        private @Nullable      Object          maxValue       = null;

        private Builder(@NotNull String id,
                        @NotNull ConfigValueType valueType,
                        @NotNull T defaultValue,
                        boolean noReload)
        {
            this.id = id;
            this.valueType = valueType;
            this.defaultValue = defaultValue;
            this.noReload = noReload;
        }

        public Builder<T> comment(@Nullable String comment) {
            if (comment == null) {
                return this;
            }

            if (!this.commentBuilder.isEmpty()) {
                this.commentBuilder.append('\n');
            }

            this.commentBuilder.append(comment.trim());
            return this;
        }

        public Builder<T> comment(@Nullable String @Nullable ... comments) {
            if (comments == null) {
                return this;
            }

            for (@Nullable String c : comments) {
                comment(c);
            }

            return this;
        }

        /**
         * 设置最小值和最大值（必须同时设置）。<br>
         * Set min and max values (must both be set together).
         * <p>
         * null 表示该侧无限制<br>
         * null means no limit on that side.
         */
        @SuppressWarnings("unchecked")
        public Builder<T> range(@Nullable Comparable<?> min,
                                @Nullable Comparable<?> max)
        {
            if (min != null && max != null) {
                if (((Comparable<Object>) min).compareTo(max) > 0) {
                    throw new IllegalArgumentException("min < max: min=" + min + ", max=" + max);
                }
            }

            this.minValue = min;
            this.maxValue = max;
            return this;
        }

        public ConfigEntry<T> build() {
            validateType(id, valueType, defaultValue);
            return new ConfigEntry<>(this);
        }

        /**
         * 校验默认值是否匹配声明的类型
         *
         * @param id    配置项 ID，用于异常提示
         * @param type  预期的配置类型
         * @param value 待校验的默认值
         */
        private void validateType(@NotNull String id,
                                  @NotNull ConfigValueType type,
                                  @NotNull Object value)
        {
            boolean valid = switch (type) {
                // 基础类型
                case STRING -> value instanceof String;
                case INT -> value instanceof Integer;
                case LONG -> value instanceof Long || value instanceof Integer; // 允许向上转型
                case DOUBLE -> value instanceof Double || value instanceof Float;
                case BOOLEAN -> value instanceof Boolean;

                case DURATION -> value instanceof ConfigDuration;
                case DATETIME -> value instanceof ConfigDateTime;

                case LIST_STRING -> checkList(value, String.class);
                case LIST_INT -> checkList(value, Integer.class);
                case LIST_LONG -> checkList(value, Long.class, Integer.class);
                case LIST_DOUBLE -> checkList(value, Double.class, Float.class);
                case LIST_BOOLEAN -> checkList(value, Boolean.class);
                case LIST_DURATION -> checkList(value, ConfigDuration.class);
                case LIST_DATETIME -> checkList(value, ConfigDateTime.class);

                case MAP -> value instanceof java.util.Map;
            };

            if (!valid) {
                throw new IllegalArgumentException(String.format(
                        "Config '%s' type mismatch: expected %s, but got %s (Value: %s)",
                        id, type, value.getClass().getSimpleName(), value
                ));
            }
        }

        /**
         * 辅助校验 List 及其泛型内容
         */
        private boolean checkList(Object value,
                                  Class<?>... allowedClasses)
        {
            if (!(value instanceof java.util.List<?> list)) {
                return false;
            }
            // 如果列表为空，默认视为合法（泛型无法在运行时完全确定）
            if (list.isEmpty()) {
                return true;
            }
            // 校验第一个元素是否在允许的类列表中
            Object firstElement = list.get(0);
            if (firstElement == null) {
                return true;
            }

            for (Class<?> clazz : allowedClasses) {
                if (clazz.isInstance(firstElement)) {
                    return true;
                }
            }
            return false;
        }
    }
}

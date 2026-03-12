package yfrp.config.type;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 表示包含「年月日时分秒」的日期时间，封装 {@link LocalDateTime}。<br>
 * Represents a datetime with year, month, day, hours, minutes, seconds.
 * <p>
 * toString 格式：{@code yyyy-MM-dd HH:mm:ss}
 */
public record ConfigDateTime(LocalDateTime value)
        implements Comparable<ConfigDateTime>
{

    public static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ConfigDateTime(LocalDateTime value) {
        this.value = Objects.requireNonNull(value, "LocalDateTime must not be null");
    }

    public static ConfigDateTime of(LocalDateTime ldt) {
        return new ConfigDateTime(ldt);
    }

    /**
     * 从字符串解析，格式：{@code yyyy-MM-dd HH:mm:ss}
     */
    public static ConfigDateTime parse(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("DateTime string is blank");
        }
        try {
            return new ConfigDateTime(LocalDateTime.parse(s.trim(), FORMATTER));
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot parse datetime (expected yyyy-MM-dd HH:mm:ss): " + s, e);
        }
    }

    @Override
    public @NotNull String toString() {
        return value.format(FORMATTER);
    }

    public long getTimestamp() {
        return value().atZone(ZoneId.systemDefault())
                .toEpochSecond();
    }

    @Override
    public int compareTo(ConfigDateTime other) {
        return this.value.compareTo(other.value);
    }

}

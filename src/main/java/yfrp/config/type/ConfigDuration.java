package yfrp.config.type;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.LongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表示包含「日时分秒」的时间段。<br>
 * Represents a duration containing days, hours, minutes, and seconds.
 * <p>
 * toString 格式：(((天d )时h )分m )秒s  （括号表示可选，若为0则省略高位）<br>
 * toString format: (((Xd )Xh )Xm )Xs  (brackets indicate optional, omit leading zeros)
 */
public record ConfigDuration(long totalSeconds)
        implements Comparable<ConfigDuration>
{

    public ConfigDuration(long totalSeconds) {
        this.totalSeconds = totalSeconds > 0
                            ? totalSeconds
                            : 0;
    }

    public static ConfigDuration ofSeconds(long seconds) {
        return new ConfigDuration(seconds);
    }

    public static ConfigDuration ofMinutes(long minutes) {
        return new ConfigDuration(minutes * 60L);
    }

    public static ConfigDuration ofHours(long hours) {
        return new ConfigDuration(hours * 3600L);
    }

    public static ConfigDuration ofDays(long days) {
        return new ConfigDuration(days * 86400L);
    }

    /**
     * 从字符串解析，支持格式：<br>
     * Parse from string, supports formats:
     * <ul>
     *   <li>{@code 1d 2h 3m 4s}</li>
     *   <li>{@code 2h 30m}</li>
     *   <li>{@code 45s}</li>
     *   <li>纯数字视为秒 / plain number treated as seconds</li>
     * </ul>
     */
    public static ConfigDuration parse(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Duration string is blank");
        }
        s = s.trim().toLowerCase();
        // plain number → seconds
        if (s.matches("-?\\d+")) {
            return new ConfigDuration(Long.parseLong(s));
        }

        long total = 0;

        Pattern p = Pattern.compile("(\\d+)\\s*(days|day|d|hours|hour|h|minutes|minute|min|m|seconds|second|s)");
        Matcher m = p.matcher(s);

        boolean found = false;
        while (m.find()) {
            found = true;
            long v = Long.parseLong(m.group(1));
            switch (m.group(2)) {
                case "days", "day", "d" -> total += v * 86400L;
                case "hours", "hour", "h" -> total += v * 3600L;
                case "minutes", "minute", "min", "m" -> total += v * 60L;
                case "seconds", "second", "s" -> total += v;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Cannot parse duration: " + s);
        }
        return new ConfigDuration(total);
    }

    public long getDays() {
        return totalSeconds / 86400L;
    }

    public long getHours() {
        return (totalSeconds % 86400L) / 3600L;
    }

    public long getMinutes() {
        return (totalSeconds % 3600L) / 60L;
    }

    public long getSeconds() {
        return totalSeconds % 60L;
    }

    public long get() {
        return totalSeconds;
    }

    /**
     * 格式：(((天d )时h )分m )秒s
     * <p>
     * 高位全为0时省略，但最少保留秒。
     */
    @Override
    public @NotNull String toString() {
        return toString((Long) null, null);
    }

    public @NotNull String toString(@Nullable Long maxValue) {
        return toString(maxValue, null);
    }

    public @NotNull String toString(@Nullable ConfigDuration maxDuration) {
        return toString(maxDuration, null);
    }

    public @NotNull String toString(@Nullable LongFunction<String> longToString) {
        return toString((Long) null, longToString);
    }

    public @NotNull String toString(@Nullable ConfigDuration maxDuration,
                                    @Nullable LongFunction<String> longToString)
    {
        return toString(
                maxDuration == null ? null : maxDuration.get(),
                longToString
        );
    }

    public @NotNull String toString(@Nullable Long maxValue,
                                    @Nullable LongFunction<String> longToString)
    {
        var isMaxValueValuable = maxValue != null
                                 && maxValue >= 0;

        var maxPrecision = isMaxValueValuable
                           ? getMaxPrecision(this.get(), maxValue)
                           : getMaxPrecision(this.get());
        Integer maxDayWidth = isMaxValueValuable
                              ? String.valueOf(maxValue / 86400).length()
                              : null;

        long d = getDays();
        long h = getHours();
        long m = getMinutes();
        long s = getSeconds();

        StringBuilder sb = new StringBuilder();

        if (maxPrecision >= 3) {
            if (d != 0) {
                if (maxDayWidth != null) {
                    sb.append(" ".repeat(Math.max(0, maxDayWidth - String.valueOf(d).length())));
                }
                sb.append(longToString == null
                          ? d
                          : longToString.apply(d));
                sb.append("d ");
            } else if (maxDayWidth != null) {
                sb.append(" ".repeat(maxDayWidth + 2));
            }
        }
        if (maxPrecision >= 2) {
            if (d != 0 || h != 0) {
                appendPaddedUnit(sb, h, longToString, "h ");
            } else {
                sb.append(" ".repeat(4));
            }
        }
        if (maxPrecision >= 1) {
            if (d != 0 || h != 0 || m != 0) {
                appendPaddedUnit(sb, m, longToString, "m ");
            } else {
                sb.append(" ".repeat(4));
            }
        }
        appendPaddedUnit(sb, s, longToString, "s");

        return sb.toString();
    }

    private static void appendPaddedUnit(StringBuilder sb,
                                         long value,
                                         @Nullable LongFunction<String> longToString,
                                         String unit)
    {
        if (value < 10) {
            sb.append(" ");
        }
        sb.append(longToString == null
                  ? value
                  : longToString.apply(value));
        sb.append(unit);
    }

    /**
     * @return {@code 0} - seconds <br>
     * {@code 1} - minutes <br>
     * {@code 2} - hours <br>
     * {@code 3} - days
     */
    private static int getMaxPrecision(long value) {
        if (value < 60) {
            // seconds
            return 0;
        }
        if (value < 3600) {
            // minutes
            return 1;
        }
        if (value < 86400) {
            // hours
            return 2;
        }
        // days
        return 3;
    }

    private static int getMaxPrecision(long v1,
                                       long v2)
    {
        return Math.max(
                getMaxPrecision(v1),
                getMaxPrecision(v2)
        );
    }

    @Override
    public int compareTo(ConfigDuration other) {
        return Long.compare(this.totalSeconds, other.totalSeconds);
    }

}

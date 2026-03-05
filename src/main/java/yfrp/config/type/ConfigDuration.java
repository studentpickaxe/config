package yfrp.config.type;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表示包含「日时分秒」的时间段。<br>
 * Represents a duration containing days, hours, minutes, and seconds.
 * <p>
 * toString 格式：(((天d )时h )分min )秒s  （括号表示可选，若为0则省略高位）<br>
 * toString format: (((Xd )Xh )Xmin )Xs  (brackets indicate optional, omit leading zeros)
 */
public record ConfigDuration(long totalSeconds)
        implements Comparable<ConfigDuration>
{

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
     *   <li>{@code 1d 2h 3min 4s}</li>
     *   <li>{@code 2h 30min}</li>
     *   <li>{@code 45s}</li>
     *   <li>纯数字视为秒 / plain number treated as seconds</li>
     * </ul>
     */
    public static ConfigDuration parse(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Duration string is blank");
        }
        s = s.trim();
        // plain number → seconds
        if (s.matches("-?\\d+")) {
            return new ConfigDuration(Long.parseLong(s));
        }

        long    total = 0;
        Pattern p     = Pattern.compile("(\\d+)\\s*(d|h|min|s)");
        Matcher m     = p.matcher(s);
        boolean found = false;
        while (m.find()) {
            found = true;
            long v = Long.parseLong(m.group(1));
            switch (m.group(2)) {
                case "d" -> total += v * 86400L;
                case "h" -> total += v * 3600L;
                case "min" -> total += v * 60L;
                case "s" -> total += v;
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

    /**
     * 格式：(((天d )时h )分min )秒s
     * <p>
     * 高位全为0时省略，但最少保留秒。
     */
    @Override
    public @NotNull String toString() {
        long          d  = getDays(), h = getHours(), min = getMinutes(), s = getSeconds();
        StringBuilder sb = new StringBuilder();
        if (d != 0) {
            sb.append(d).append("d ");
        }
        if (d != 0 || h != 0) {
            sb.append(h).append("h ");
        }
        if (d != 0 || h != 0 || min != 0) {
            sb.append(min).append("min ");
        }
        sb.append(s).append("s");
        return sb.toString().trim();
    }

    @Override
    public int compareTo(ConfigDuration other) {
        return Long.compare(this.totalSeconds, other.totalSeconds);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConfigDuration that)) {
            return false;
        }
        return totalSeconds == that.totalSeconds;
    }

}

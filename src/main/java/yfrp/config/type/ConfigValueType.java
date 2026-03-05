package yfrp.config.type;

/**
 * 配置项值类型枚举。
 * <p>
 * Enum for configuration entry value types.
 */
public enum ConfigValueType {
    /**
     * 字符串
     * <p>
     * String
     */
    STRING,

    /**
     * 长整型
     * <p>
     * Long integer
     */
    LONG,

    /**
     * 整型<p>Integer
     */
    INT,

    /**
     * 双精度浮点<p>Double precision float
     */
    DOUBLE,

    /**
     * 时间（包含 日时分秒）
     * <p>
     * Duration (contains days, hours, minutes, seconds)
     */
    DURATION,

    /**
     * 日期时间（包含 年月日时分秒）
     * <p>
     * DateTime (contains year, month, day, hours, minutes, seconds)
     */
    DATETIME,

    /**
     * 布尔值
     * <p>
     * Boolean
     */
    BOOLEAN,

    /**
     * 字符串列表
     * <p>
     * List of strings
     */
    LIST_STRING,

    /**
     * 长整型列表
     * <p>
     * List of longs
     */
    LIST_LONG,

    /**
     * 整型列表
     * <p>
     * List of ints
     */
    LIST_INT,

    /**
     * 双精度浮点列表
     * <p>
     * List of doubles
     */
    LIST_DOUBLE,

    /**
     * 时间列表
     * <p>
     * List of durations
     */
    LIST_DURATION,

    /**
     * 日期时间列表
     * <p>
     * List of datetimes
     */
    LIST_DATETIME,

    /**
     * 布尔值列表
     * <p>
     * List of booleans
     */
    LIST_BOOLEAN
}

package yfrp.config.type;

/**
 * 配置项值类型枚举。<br>
 * Enum for configuration entry value types.
 */
public enum ConfigValueType {
    /**
     * 字符串<br>
     * String
     */
    STRING,

    /**
     * 长整型<br>
     * Long integer
     */
    LONG,

    /**
     * 整型<br>
     * Integer
     */
    INT,

    /**
     * 双精度浮点<br>
     * Double precision float
     */
    DOUBLE,

    /**
     * 时间（包含 日时分秒）<br>
     * Duration (contains days, hours, minutes, seconds)
     */
    DURATION,

    /**
     * 日期时间（包含 年月日时分秒）<br>
     * DateTime (contains year, month, day, hours, minutes, seconds)
     */
    DATETIME,

    /**
     * 布尔值<br>
     * Boolean
     */
    BOOLEAN,

    /**
     * 字符串列表<br>
     * List of strings
     */
    LIST_STRING,

    /**
     * 长整型列表<br>
     * List of longs
     */
    LIST_LONG,

    /**
     * 整型列表<br>
     * List of ints
     */
    LIST_INT,

    /**
     * 双精度浮点列表<br>
     * List of doubles
     */
    LIST_DOUBLE,

    /**
     * 时间列表<br>
     * List of durations
     */
    LIST_DURATION,

    /**
     * 日期时间列表<br>
     * List of datetimes
     */
    LIST_DATETIME,

    /**
     * 布尔值列表<br>
     * List of booleans
     */
    LIST_BOOLEAN,

    /**
     * 字符串到任意对象的映射<br>
     * Map of string keys to objects
     */
    MAP,
}

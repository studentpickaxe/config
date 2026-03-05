package yfrp.config.format;

/**
 * 配置文件格式枚举，在构造 ConfigManager 时传入，决定读写格式。
 * <p>
 * Config file format enum, passed in the ConfigManager constructor to determine read/write format.
 */
public enum ConfigFormat {
    /**
     * JSON5 格式（支持注释、尾逗号等扩展语法）
     * <p>
     * JSON5 format (supports comments, trailing commas, and other extended syntax)
     */
    JSON5,

    /**
     * YAML 格式
     * <p>
     * YAML format
     */
    YAML
}

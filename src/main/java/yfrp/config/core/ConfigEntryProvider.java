package yfrp.config.core;

import yfrp.config.entry.ConfigEntry;
import org.jetbrains.annotations.NotNull;

/**
 * 配置项枚举必须实现此接口，以将枚举常量关联到 {@link ConfigEntry} 定义。<br>
 * Config entry enums must implement this interface to link enum constants to {@link ConfigEntry} definitions.
 *
 * <h3>示例 / Example</h3>
 * <pre>{@code
 * public enum AppConfig implements ConfigEntryProvider {
 *     SERVER_PORT(
 *         ConfigEntry.builder("server.port", ConfigValueType.INT, 8080, false)
 *             .comment("HTTP 服务端口 / HTTP server port")
 *             .range(1, 65535)
 *             .build()
 *     );
 *
 *     private final ConfigEntry<?> entry;
 *     AppConfig(ConfigEntry<?> entry) { this.entry = entry; }
 *
 *     @Override
 *     public ConfigEntry<?> getEntry() { return entry; }
 * }
 * }</pre>
 */
public interface ConfigEntryProvider {

    /**
     * 返回此枚举常量对应的配置项定义。<br>
     * Returns the config entry definition for this enum constant.
     *
     * @return non-null {@link ConfigEntry}
     */
    @NotNull
    ConfigEntry<?> getEntry();
}

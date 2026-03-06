package yfrp.config.core;

import org.jetbrains.annotations.NotNull;
import yfrp.config.entry.ConfigEntry;

/**
 * 配置项枚举必须实现此接口，以将枚举常量关联到 {@link ConfigEntry} 定义。<br>
 * Config entry enums must implement this interface to link enum constants to {@link ConfigEntry} definitions.
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

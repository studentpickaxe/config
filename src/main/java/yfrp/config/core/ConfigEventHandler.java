package yfrp.config.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 配置管理器事件回调接口。<br>
 * Event callbacks for ConfigManager lifecycle events.
 */
public interface ConfigEventHandler {

    /**
     * 配置文件不存在，已用默认值创建。<br>
     * Config file not found; created with defaults.
     *
     * @param absolutePath 文件绝对路径 / absolute path of the created file
     */
    void onFileCreated(@NotNull String absolutePath);

    /**
     * 配置文件已重载。<br>
     * Config file reloaded.
     *
     * @param absolutePath 文件绝对路径 / absolute path of the reloaded file
     */
    void onReloaded(@NotNull String absolutePath);

    /**
     * 某配置项的值超出范围，已回退到默认值。<br>
     * Entry value out of range; falling back to default.
     *
     * @param id  配置项 id / entry id
     * @param raw 文件中读取到的原始值 / raw value from file
     * @param min 最小值（null 表示无限制）/ min value (null = no limit)
     * @param max 最大值（null 表示无限制）/ max value (null = no limit)
     */
    void onOutOfRange(@NotNull String id,
                      @Nullable Object raw,
                      @Nullable Object min,
                      @Nullable Object max,
                      @NotNull Object defaultVal);

    /**
     * ValueConverter 转换标量时类型不匹配，已丢弃该值。<br>
     * ValueConverter encountered a type mismatch for a scalar; value discarded.
     *
     * @param expectedType 期望的类型名 / expected type name
     * @param actualValue  给定的错误值 / the invalid value given
     */
    void onConversionFailed(@NotNull String expectedType,
                            @Nullable Object actualValue);

    /**
     * ValueConverter 往列表添加元素时类型不匹配，已丢弃该元素。<br>
     * ValueConverter encountered a type mismatch for a list element; element discarded.
     *
     * @param expectedType 期望的元素类型名 / expected element type name
     * @param actualValue  给定的错误值 / the invalid value given
     */
    void onListElementConversionFailed(@NotNull String expectedType,
                                       @Nullable Object actualValue);
}

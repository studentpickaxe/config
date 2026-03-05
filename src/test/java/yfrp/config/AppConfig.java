package yfrp.config;

import org.jetbrains.annotations.NotNull;
import yfrp.config.core.ConfigEntryProvider;
import yfrp.config.entry.ConfigEntry;
import yfrp.config.type.ConfigDateTime;
import yfrp.config.type.ConfigDuration;
import yfrp.config.type.ConfigValueType;

import java.time.LocalDateTime;
import java.util.List;

public enum AppConfig implements ConfigEntryProvider {

    // 字符串，带注释
    APP_NAME(ConfigEntry.builder("app.name", ConfigValueType.STRING, "MyApp", false)
            .comment("应用名称\nApplication name")
            .build()),

    // 整数，带范围，带注释
    SERVER_PORT(ConfigEntry.builder("server.port", ConfigValueType.INT, 8080, false)
            .comment("HTTP 服务端口\nHTTP server port")
            .range(1, 65535)
            .build()),

    // 无法重载
    DB_URL(ConfigEntry.builder("database.url", ConfigValueType.STRING,
                    "jdbc:mysql://localhost/db", true)
            .comment("数据库连接（无法重载）\nDatabase URL (no-reload)")
            .build()),

    // 时间类型
    SESSION_TIMEOUT(ConfigEntry.builder("auth.sessionTimeout",
                    ConfigValueType.DURATION, ConfigDuration.ofMinutes(30), false)
            .comment("会话超时\nSession timeout")
            .range(ConfigDuration.ofSeconds(60), ConfigDuration.ofDays(7))
            .build()),

    // 日期时间
    LAUNCH_DATE(ConfigEntry.builder("app.launchDate", ConfigValueType.DATETIME,
                    ConfigDateTime.of(LocalDateTime.of(2024, 1, 1, 0, 0, 0)), false)
            .build()),

    // 字符串列表
    ALLOWED_HOSTS(ConfigEntry.builder("server.allowedHosts",
                    ConfigValueType.LIST_STRING, List.of("localhost"), false)
            .build());

    private final ConfigEntry<?> entry;

    AppConfig(ConfigEntry<?> entry) {
        this.entry = entry;
    }

    @Override
    public @NotNull ConfigEntry<?> getEntry() {
        return entry;
    }
}

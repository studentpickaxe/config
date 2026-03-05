# Enterprise Config Library

Java 配置管理库，同时支持 **JSON5**（带注释）和 **YAML** 格式。

---

## 特性 Features

- ✅ **JSON5**（via hjson）：支持 `//` 注释、尾逗号
- ✅ **YAML**（via SnakeYAML 2.x）
- ✅ 格式由构造时传入的 `ConfigFormat` 枚举决定
- ✅ 支持所有值类型：`STRING` / `INT` / `LONG` / `DOUBLE` / `BOOLEAN` / `DURATION` / `DATETIME` 及对应 `LIST_*`
- ✅ 数字类型强转（非数字则丢弃并使用默认值）
- ✅ `DURATION` 格式：`(((天d )时h )分min )秒s`（高位全0时省略）
- ✅ `DATETIME` 格式：`yyyy-MM-dd HH:mm:ss`
- ✅ 配置项注释（注释写在配置项前，缩进相同）
- ✅ **noReload**：标记为 `true` 的项仅首次加载生效，重载时保留旧值
- ✅ **min/max 范围校验**：必须成对设置（可为 null 表示单侧无限制）；保存时在同行用中英双语标注
- ✅ `DURATION`/`DATETIME` 列表保存时对齐各项
- ✅ 原子写入（先写 `.tmp` 再 `ATOMIC_MOVE`）
- ✅ 读写锁保护，线程安全

---

## 依赖 Dependencies

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.hjson</groupId>
    <artifactId>hjson</artifactId>
    <version>3.0.0</version>
</dependency>
<dependency>
    <groupId>org.yaml</groupId>
    <artifactId>snakeyaml</artifactId>
    <version>2.2</version>
</dependency>
```

---

## 快速上手 Quick Start

### 1. 定义配置项枚举

```java
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
    AppConfig(ConfigEntry<?> entry) { this.entry = entry; }

    @Override
    public ConfigEntry<?> getEntry() { return entry; }
}
```

### 2. 创建 ConfigManager 并加载

```java
// JSON5 格式
ConfigManager<AppConfig> cfg = new ConfigManager<>(
    Path.of("config.json5"), ConfigFormat.JSON5, AppConfig.class);
cfg.load();  // 不存在则自动创建含默认值的文件

// 读取
String name    = cfg.getString(AppConfig.APP_NAME);
int port       = cfg.getInt(AppConfig.SERVER_PORT);
ConfigDuration timeout = cfg.getDuration(AppConfig.SESSION_TIMEOUT);
List<String> hosts = cfg.getStringList(AppConfig.ALLOWED_HOSTS);

// 重载（noReload 项不会更新）
cfg.load();

// 运行时修改 + 保存
cfg.set(AppConfig.SERVER_PORT, 9090);
cfg.save();
```

---

## 生成的文件示例 Sample Output

### JSON5

```json5
{
  "app": {
    // 应用名称
    // Application name
    "name": "MyApp",
    // HTTP 服务端口
    // HTTP server port
    "port": 8080,  // 最小值/Min: 1  最大值/Max: 65535
  },
  "database": {
    // 数据库连接（无法重载）
    // Database URL (no-reload)
    // [无法重载] 此项仅在首次加载时生效，重载时将被忽略。
    // [No-Reload] This entry only takes effect on first load and will be ignored on reload.
    "url": "jdbc:mysql://localhost/db",
  },
  "auth": {
    // 会话超时
    // Session timeout
    "sessionTimeout": "30min 0s",  // 最小值/Min: 60s  最大值/Max: 7d 0h 0min 0s
  }
}
```

### YAML

```yaml
app:
  # 应用名称
  # Application name
  name: MyApp
database:
  # 数据库连接（无法重载）
  # Database URL (no-reload)
  # [无法重载] 此项仅在首次加载时生效，重载时将被忽略。
  # [No-Reload] This entry only takes effect on first load and will be ignored on reload.
  url: "jdbc:mysql://localhost/db"
auth:
  # 会话超时
  # Session timeout
  sessionTimeout: "30min 0s"  # 最小值/Min: 60s  最大值/Max: 7d 0h 0min 0s
```

---

## id 命名规则 ID Rules

- 用 `.` 分隔层级，例如 `server.http.port`
- 不能为 `null`
- 每个段不能为空或纯空白（禁止 `a..b`、`.a`、`a.`、`a.  .b`）

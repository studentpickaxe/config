package yfrp.config.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import yfrp.config.entry.ConfigEntry;
import yfrp.config.format.ConfigFormat;
import yfrp.config.type.ConfigDateTime;
import yfrp.config.type.ConfigDuration;
import yfrp.config.type.ConfigValueType;
import yfrp.config.util.ConfigParser;
import yfrp.config.util.ConfigSerializer;
import yfrp.config.util.ValueConverter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * <h3>使用示例 / Usage Example</h3>
 * <pre>{@code
 * // 定义配置项枚举 / Define entry enum
 * public enum MyConfig implements ConfigEntryProvider {
 *     SERVER_PORT(ConfigEntry.builder("server.port", ConfigValueType.INT, 8080, false)
 *         .comment("HTTP 服务端口\nHTTP server port")
 *         .range(1, 65535)
 *         .build()),
 *     DB_URL(ConfigEntry.builder("database.url", ConfigValueType.STRING, "jdbc:mysql://localhost/db", true)
 *         .comment("数据库连接地址（无法重载）\nDatabase URL (no-reload)")
 *         .build());
 *
 *     private final ConfigEntry<?> entry;
 *     MyConfig(ConfigEntry<?> entry) { this.entry = entry; }
 *
 *     @Override public ConfigEntry<?> getEntry() { return entry; }
 * }
 *
 * // 创建管理器 / Create manager
 * ConfigManager<MyConfig> cfg = new ConfigManager<>(
 *     Path.of("config.json5"), ConfigFormat.JSON5, MyConfig.class);
 * cfg.load();
 *
 * // 读取值 / Read value
 * int port = cfg.getInt(MyConfig.SERVER_PORT);
 * }</pre>
 *
 * <p>线程安全：使用读写锁保护内部状态。
 * Thread-safe: uses a read-write lock to protect internal state.
 *
 * @param <E> 配置项枚举类型，必须实现 {@link ConfigEntryProvider}
 *            Config entry enum type, must implement {@link ConfigEntryProvider}
 */
public final class ConfigManager<E extends Enum<E> & ConfigEntryProvider> {

    private final Path         configPath;
    private final ConfigFormat format;
    private final E[]          enumConstants;

    @Nullable
    private final ConfigEventHandler handler;

    @NotNull
    private final ValueConverter valueConverter;

    /**
     * 当前配置值（平铺 key → 转换后的 Java 值）
     */
    private final Map<String, Object> values     = new LinkedHashMap<>();
    /**
     * 已锁定（noReload）且已首次加载的 key 集合
     */
    private final Set<String>         lockedKeys = new HashSet<>();

    private final    ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private volatile boolean       loaded = false;

    // ══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * @param configPath 配置文件路径 / Config file path
     * @param format     文件格式 / File format
     * @param enumClass  配置项枚举类 / Config entry enum class
     */
    public ConfigManager(@NotNull Path configPath,
                         @NotNull ConfigFormat format,
                         @NotNull Class<E> enumClass,
                         @Nullable ConfigEventHandler handler)
    {
        this.configPath = Objects.requireNonNull(configPath, "configPath");
        this.format = Objects.requireNonNull(format, "format");
        this.enumConstants = Objects.requireNonNull(enumClass, "enumClass").getEnumConstants();
        if (this.enumConstants == null || this.enumConstants.length == 0) {
            throw new IllegalArgumentException("Enum " + enumClass.getName() + " has no constants");
        }
        this.handler = handler;
        this.valueConverter = new ValueConverter(handler);
        validateEntries();
    }

    /**
     * 验证枚举中所有配置项定义的合法性
     */
    private void validateEntries() {
        Set<String> ids = new LinkedHashSet<>();
        for (E e : enumConstants) {
            ConfigEntry<?> entry = e.getEntry();
            if (!ids.add(entry.getId())) {
                throw new IllegalStateException(
                        "Duplicate config entry id: " + entry.getId());
            }
        }
    }

// ══════════════════════════════════════════════════════════════════════════
// Reload（唯一的公开 load/save 入口）
// ══════════════════════════════════════════════════════════════════════════

    /**
     * 重载配置：若文件不存在则以默认值创建，否则读取文件并回写（补全缺失键/刷新注释）。<br>
     * Reload config: create with defaults if file absent, otherwise read then write back.
     *
     * @throws IOException on I/O failure
     */
    public void reload()
            throws IOException
    {
        rwLock.writeLock().lock();
        try {
            String abs = configPath.toAbsolutePath().toString();
            if (!Files.exists(configPath)) {
                applyDefaults(true);
                saveInternal();
                loaded = true;
                if (handler != null) {
                    handler.onFileCreated(abs);
                }
                return;
            }
            String              text    = Files.readString(configPath, StandardCharsets.UTF_8);
            Map<String, Object> rawFlat = ConfigParser.parse(text, format);
            mergeValues(rawFlat, !loaded);
            loaded = true;
            saveInternal();
            if (handler != null) {
                handler.onReloaded(abs);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Internal save
    // ══════════════════════════════════════════════════════════════════════════

    private void saveInternal()
            throws IOException
    {
        Path parent = configPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<ConfigEntry<?>> entries = new ArrayList<>();
        for (E e : enumConstants) {
            entries.add(e.getEntry());
        }

        String text = ConfigSerializer.serialize(
                Collections.unmodifiableMap(values), entries, format);

        Path tmp = configPath.resolveSibling(configPath.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, configPath,
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // ── Internal merge logic ──────────────────────────────────────────────────

    private void mergeValues(Map<String, Object> rawFlat,
                             boolean firstLoad)
    {
        for (E e : enumConstants) {
            ConfigEntry<?> entry = e.getEntry();
            String         id    = entry.getId();

            if (lockedKeys.contains(id)) {
                continue;
            }

            Object raw       = rawFlat.get(id);
            Object converted = (raw != null) ? valueConverter.convert(raw, entry.getValueType()) : null;

            if (converted != null && checkRange(converted, entry)) {
                values.put(id, converted);
            } else {
                if (raw != null && handler != null) {
                    handler.onOutOfRange(id, raw, entry.getMinValue(), entry.getMaxValue());
                }
                values.put(id, entry.getDefaultValue());
            }

            if (firstLoad && entry.isNoReload()) {
                lockedKeys.add(id);
            }
        }
    }

    private void applyDefaults(boolean firstLoad) {
        for (E e : enumConstants) {
            ConfigEntry<?> entry = e.getEntry();
            values.put(entry.getId(), entry.getDefaultValue());
            if (firstLoad && entry.isNoReload()) {
                lockedKeys.add(entry.getId());
            }
        }
    }

    private Object convert(Object raw,
                           ConfigEntry<?> entry)
    {
        return valueConverter.convert(raw, entry.getValueType());
    }

    private boolean checkRange(Object value,
                               ConfigEntry<?> entry)
    {
        if (!entry.hasRange()) {
            return true;
        }
        // For lists, check each element
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (!valueConverter.inRange(item, entry.getMinValue(), entry.getMaxValue())) {
                    return false;
                }
            }
            return true;
        }
        return valueConverter.inRange(value, entry.getMinValue(), entry.getMaxValue());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Value accessors
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 获取任意类型的原始值，不存在返回 null。<br>
     * Get raw value for any entry, returns null if absent.
     */
    @Nullable
    public Object getRaw(@NotNull E key) {
        rwLock.readLock().lock();
        try {
            return values.get(key.getEntry().getId());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 获取字符串值<br>
     * Get string value
     */
    @NotNull
    public String getString(@NotNull E key) {
        Object v = requireValue(key, ConfigValueType.STRING);
        return v != null ? v.toString() : (String) key.getEntry().getDefaultValue();
    }

    /**
     * 获取 int 值<br>
     * Get int value
     */
    public int getInt(@NotNull E key) {
        Object v = requireValue(key, ConfigValueType.INT);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return (Integer) key.getEntry().getDefaultValue();
    }

    /**
     * 获取 long 值<br>
     * Get long value
     */
    public long getLong(@NotNull E key) {
        Object v = requireValue(key, ConfigValueType.LONG);
        if (v instanceof Number n) {
            return n.longValue();
        }
        return (Long) key.getEntry().getDefaultValue();
    }

    /**
     * 获取 double 值<br>
     * Get double value
     */
    public double getDouble(@NotNull E key) {
        Object v = requireValue(key, ConfigValueType.DOUBLE);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return (Double) key.getEntry().getDefaultValue();
    }

    /**
     * 获取布尔值<br>
     * Get boolean value
     */
    public boolean getBoolean(@NotNull E key) {
        Object v = requireValue(key, ConfigValueType.BOOLEAN);
        if (v instanceof Boolean b) {
            return b;
        }
        return (Boolean) key.getEntry().getDefaultValue();
    }

    /**
     * 获取时间值<br>
     * Get duration value
     */
    @NotNull
    public ConfigDuration getDuration(@NotNull E key) {
        Object v = requireValue(key, ConfigValueType.DURATION);
        if (v instanceof ConfigDuration d) {
            return d;
        }
        return (ConfigDuration) key.getEntry().getDefaultValue();
    }

    /**
     * 获取日期时间值<br>
     * Get datetime value
     */
    @NotNull
    public ConfigDateTime getDateTime(@NotNull E key) {
        Object v = requireValue(key, ConfigValueType.DATETIME);
        if (v instanceof ConfigDateTime dt) {
            return dt;
        }
        return (ConfigDateTime) key.getEntry().getDefaultValue();
    }

    /**
     * 获取字符串列表<br>
     * Get list of strings
     */
    @NotNull
    public List<String> getStringList(@NotNull E key) {
        return getListOrDefault(key, ConfigValueType.LIST_STRING);
    }

    /**
     * 获取 long 列表<br>
     * Get list of longs
     */
    @NotNull
    public List<Long> getLongList(@NotNull E key) {
        return getListOrDefault(key, ConfigValueType.LIST_LONG);
    }

    /**
     * 获取 int 列表<br>
     * Get list of ints
     */
    @NotNull
    public List<Integer> getIntList(@NotNull E key) {
        return getListOrDefault(key, ConfigValueType.LIST_INT);
    }

    /**
     * 获取 double 列表<br>
     * Get list of doubles
     */
    @NotNull
    public List<Double> getDoubleList(@NotNull E key) {
        return getListOrDefault(key, ConfigValueType.LIST_DOUBLE);
    }

    /**
     * 获取布尔值列表<br>
     * Get list of booleans
     */
    @NotNull
    public List<Boolean> getBooleanList(@NotNull E key) {
        return getListOrDefault(key, ConfigValueType.LIST_BOOLEAN);
    }

    /**
     * 获取时间列表<br>
     * Get list of durations
     */
    @NotNull
    public List<ConfigDuration> getDurationList(@NotNull E key) {
        return getListOrDefault(key, ConfigValueType.LIST_DURATION);
    }

    /**
     * 获取日期时间列表<br>
     * Get list of datetimes
     */
    @NotNull
    public List<ConfigDateTime> getDateTimeList(@NotNull E key) {
        return getListOrDefault(key, ConfigValueType.LIST_DATETIME);
    }

    /**
     * 获取 Map 值<br>
     * Get map value
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(@NotNull E key) {
        Object v = requireValue(key, ConfigValueType.MAP);
        if (v instanceof Map<?, ?> m) {
            return Collections.unmodifiableMap((Map<String, Object>) m);
        }
        Object def = key.getEntry().getDefaultValue();
        if (def instanceof Map<?, ?> m) {
            return Collections.unmodifiableMap((Map<String, Object>) m);
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> getListOrDefault(E key,
                                         ConfigValueType expectedType)
    {
        Object v = requireValue(key, expectedType);
        if (v instanceof List<?> l) {
            return (List<T>) l;
        }
        Object def = key.getEntry().getDefaultValue();
        if (def instanceof List<?> l) {
            return (List<T>) l;
        }
        return Collections.emptyList();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Programmatic set (runtime override)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 运行时设置一个配置项的值（跳过 noReload 限制，但检查范围）。<br>
     * Set a config entry value at runtime (bypasses noReload, checks range).
     */
    public <T> void set(@NotNull E key,
                        @Nullable T value)
    {
        ConfigEntry<?> entry = key.getEntry();
        rwLock.writeLock().lock();
        try {
            if (value == null) {
                values.put(entry.getId(), entry.getDefaultValue());
            } else {
                Object converted = valueConverter.convert(value, entry.getValueType());
                if (converted == null) {
                    throw new IllegalArgumentException(
                            "Cannot convert value for entry '" + entry.getId() + "': " + value);
                }
                if (!checkRange(converted, entry)) {
                    throw new IllegalArgumentException(
                            "Value " + value + " is out of range for entry '" + entry.getId() + "'");
                }
                values.put(entry.getId(), converted);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    private Object requireValue(E key,
                                ConfigValueType expectedType)
    {
        ConfigEntry<?> entry = key.getEntry();
        if (entry.getValueType() != expectedType) {
            throw new IllegalArgumentException(
                    "Entry '" + entry.getId() + "' has type " + entry.getValueType() +
                    " but was accessed as " + expectedType);
        }
        rwLock.readLock().lock();
        try {
            return values.getOrDefault(entry.getId(), entry.getDefaultValue());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 是否已加载<br>
     * Whether config has been loaded
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * 获取配置文件路径<br>
     * Get config file path
     */
    @NotNull
    public Path getConfigPath() {
        return configPath;
    }

    /**
     * 获取配置文件格式<br>
     * Get config file format
     */
    @NotNull
    public ConfigFormat getFormat() {
        return format;
    }

    /**
     * 只读的当前值快照<br>
     * Read-only snapshot of current values
     */
    @NotNull
    public Map<String, Object> snapshot() {
        rwLock.readLock().lock();
        try {
            return Collections.unmodifiableMap(new LinkedHashMap<>(values));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public String toString() {
        return "ConfigManager{path=" + configPath + ", format=" + format +
               ", entries=" + enumConstants.length + ", loaded=" + loaded + "}";
    }
}

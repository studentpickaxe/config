package yfrp.config;

import yfrp.config.core.ConfigManager;
import yfrp.config.format.ConfigFormat;
import yfrp.config.type.ConfigDuration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class Test {
    public static void main(String[] args) throws IOException {
        // JSON5 格式
        ConfigManager<AppConfig> cfg = new ConfigManager<>(
                Path.of("config.json5"), ConfigFormat.JSON5, AppConfig.class);
        cfg.load();  // 不存在则自动创建含默认值的文件

        // 读取
        String         name    = cfg.getString(AppConfig.APP_NAME);
        int            port    = cfg.getInt(AppConfig.SERVER_PORT);
        ConfigDuration timeout = cfg.getDuration(AppConfig.SESSION_TIMEOUT);
        List<String>   hosts   = cfg.getStringList(AppConfig.ALLOWED_HOSTS);

        // 重载（noReload 项不会更新）
        cfg.load();

        // 运行时修改 + 保存
        cfg.set(AppConfig.SERVER_PORT, 9090);
        cfg.save();
    }
}

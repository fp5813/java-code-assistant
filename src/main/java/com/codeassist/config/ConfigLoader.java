package com.codeassist.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 配置加载器。
 * 加载优先级：.env 文件 > settings.json > 硬编码默认值。
 * settings.json 的搜索路径：
 *   1. 项目根目录下的 .ca/settings.json
 *   2. 用户主目录下的 .claude/settings.json（兼容 Claude Code）
 *   3. 用户主目录下的 .ca/settings.json
 */
public class ConfigLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);

    private static final String[] SETTINGS_PATHS = {
        ".ca/settings.json",
        ".claude/settings.json"
    };

    private static final String ENV_FILE = ".env";
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ConfigLoader() {}

    /**
     * 加载配置，按优先级合并：.env 覆盖 settings.json。
     */
    public static AppConfig load() {
        AppConfig config = loadSettingsJson();
        applyDotEnv(config);
        applySystemEnv(config);
        return config;
    }

    /**
     * 加载 settings.json。
     */
    private static AppConfig loadSettingsJson() {
        // 先尝试项目根目录
        String userHome = System.getProperty("user.home");
        for (String relative : SETTINGS_PATHS) {
            // 尝试项目目录
            Path projectPath = Paths.get(relative);
            if (Files.exists(projectPath)) {
                return readJsonFile(projectPath);
            }
            // 尝试用户主目录
            Path homePath = Paths.get(userHome, relative);
            if (Files.exists(homePath)) {
                return readJsonFile(homePath);
            }
        }
        return new AppConfig();
    }

    private static AppConfig readJsonFile(Path path) {
        try {
            return MAPPER.readValue(path.toFile(), AppConfig.class);
        } catch (IOException e) {
            LOG.warn("Failed to read config file: {} - {}", path, e.getMessage());
            return new AppConfig();
        }
    }

    /**
     * 从 .env 文件加载环境变量（覆盖 settings.json 中的值）。
     */
    private static void applyDotEnv(AppConfig config) {
        Path envPath = Paths.get(ENV_FILE);
        if (!Files.exists(envPath)) {
            return;
        }
        try {
            Map<String, String> envMap = new HashMap<>();
            if (config.getEnv() != null) {
                envMap.putAll(config.getEnv());
            }
            for (String line : Files.readAllLines(envPath)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String value = line.substring(eqIdx + 1).trim();
                    if (!value.isEmpty()) {
                        envMap.put(key, value);
                    }
                }
            }
            // 如果 .env 中有 ANTHROPIC_MODEL，也更新 model 字段
            if (envMap.containsKey("ANTHROPIC_MODEL")) {
                // config.model 已有值但可能被 .env 覆盖
            }
            config.setEnv(envMap);
        } catch (IOException e) {
            LOG.warn("Failed to read .env file: {}", e.getMessage());
        }
    }

    /**
     * 从系统环境变量读取配置（优先级最高）。
     */
    private static void applySystemEnv(AppConfig config) {
        Map<String, String> envMap = config.getEnv();
        if (envMap == null) {
            envMap = new HashMap<>();
        }

        String[] envVars = {"ANTHROPIC_BASE_URL", "ANTHROPIC_AUTH_TOKEN", "ANTHROPIC_MODEL"};
        for (String var : envVars) {
            String sysVal = System.getenv(var);
            if (sysVal != null && !sysVal.isEmpty()) {
                envMap.put(var, sysVal);
            }
        }

        config.setEnv(envMap);
    }
}

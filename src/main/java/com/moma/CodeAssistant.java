package com.moma;

import com.moma.cli.CliApp;
import com.moma.config.AppConfig;
import com.moma.config.ConfigLoader;
import com.moma.di.ApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 墨码 (MoMa) 主入口。
 *
 * <p>以 AI 为笔，挥洒自如地编写代码。基于 LangChain4j 的智能体实现。</p>
 *
 * <p>启动流程：</p>
 * <ol>
 *   <li>加载配置</li>
 *   <li>初始化 DI 容器（扫描组件、创建 Bean、注入依赖）</li>
 *   <li>启动 REPL 交互循环</li>
 * </ol>
 */
public class CodeAssistant {

    private static final Logger LOG = LoggerFactory.getLogger(CodeAssistant.class);

    public static void main(String[] args) {
        // 显示启动信息
        printBanner();

        // 加载配置
        AppConfig config = loadConfig();
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            System.err.println();
            System.err.println("\u001B[31m╔════════════════════════════════════════════════╗\u001B[0m");
            System.err.println("\u001B[31m║  错误: 未配置 API Key                       ║\u001B[0m");
            System.err.println("\u001B[31m╠════════════════════════════════════════════════╣\u001B[0m");
            System.err.println("\u001B[31m║  请执行以下步骤:                              ║\u001B[0m");
            System.err.println("\u001B[31m║  1. cp .env.example .env                      ║\u001B[0m");
            System.err.println("\u001B[31m║  2. 编辑 .env 填入 API Key                     ║\u001B[0m");
            System.err.println("\u001B[31m║  或: cp settings.example.json ~/.ca/settings.json║\u001B[0m");
            System.err.println("\u001B[31m╚════════════════════════════════════════════════╝\u001B[0m");
            System.err.println();
            System.exit(1);
        }

        LOG.info("配置加载完成: model={}, baseUrl={}", config.getModelName(), config.getBaseUrl());

        // ── 初始化 DI 容器 ──
        ApplicationContext appContext = new ApplicationContext();

        // 注册配置实例作为属性源
        java.util.Map<String, String> props = new java.util.HashMap<>();
        if (config.getEnv() != null) {
            props.putAll(config.getEnv());
        }
        props.put("redis.host", config.getEnv() != null && config.getEnv().containsKey("REDIS_HOST")
            ? config.getEnv().get("REDIS_HOST") : "localhost");
        props.put("redis.port", config.getEnv() != null && config.getEnv().containsKey("REDIS_PORT")
            ? config.getEnv().get("REDIS_PORT") : "6379");
        props.put("cache.local.maxSize", "1000");
        props.put("cache.local.defaultTtl", "300");
        appContext.setPropertySource(props);

        // 注册 DiConfig（配置类，内含所有 @Bean 方法）
        appContext.register(com.moma.config.DiConfig.class);

        // 刷新容器（创建所有 Bean）
        appContext.refresh();

        // ── 获取 CliApp 并启动 REPL ──
        CliApp app = appContext.getBean(CliApp.class);
        app.start();
    }

    private static AppConfig loadConfig() {
        try {
            return ConfigLoader.load();
        } catch (Exception e) {
            LOG.error("配置加载失败", e);
            return new AppConfig();
        }
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════╗");
        System.out.println("  ║       墨码 (MoMa) v1.0.0             ║");
        System.out.println("  ║  以 AI 为笔，挥洒自如地编写代码     ║");
        System.out.println("  ║  重构: DI + Redis + 多线程并发       ║");
        System.out.println("  ╚══════════════════════════════════════╝");
        System.out.println();
    }
}

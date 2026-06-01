package com.codeassist;

import com.codeassist.cli.CliApp;
import com.codeassist.config.AppConfig;
import com.codeassist.config.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Java Code Assistant 主入口。
 *
 * <p>基于 LangChain4j 的 AI 编程助手，具备 perceive-think-act 闭环。</p>
 *
 * <p>启动方式：</p>
 * <pre>
 * mvn package
 * java -jar target/java-code-assistant.jar
 * </pre>
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

        // 启动 REPL
        CliApp app = new CliApp(config);
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
        System.out.println("  ║     Java Code Assistant v1.0.0      ║");
        System.out.println("  ║  基于 LangChain4j 的 AI 编程助手    ║");
        System.out.println("  ╚══════════════════════════════════════╝");
        System.out.println();
    }
}

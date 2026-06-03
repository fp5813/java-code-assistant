package com.moma.learning;

import com.moma.di.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 代码模式学习器。分析项目代码结构和 Git 历史，总结开发模式。
 */
@Component
public class PatternLearner {

    private static final Logger LOG = LoggerFactory.getLogger(PatternLearner.class);

    /** 上次扫描时间戳 */
    private long lastScanTime = 0;

    /** 缓存的代码分析结果 */
    private String cachedCodebaseReport;

    /** 代码变更检测阈值（分钟） */
    private static final int STALE_MINUTES = 5;

    public PatternLearner() {
    }

    /**
     * 检查分析结果是否过期（距上次扫描超过阈值或检测到源码变更）。
     */
    public boolean isStale() {
        if (lastScanTime == 0) return true;
        long elapsed = System.currentTimeMillis() - lastScanTime;
        if (elapsed > STALE_MINUTES * 60_000L) return true;

        // 检查是否有源码文件变更
        try {
            Path srcDir = Path.of(System.getProperty("user.dir"), "src", "main", "java", "com", "moma");
            if (Files.exists(srcDir)) {
                try (Stream<Path> files = Files.walk(srcDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))) {
                    return files.anyMatch(f -> {
                        try {
                            return Files.getLastModifiedTime(f).toMillis() > lastScanTime;
                        } catch (Exception e) {
                            return false;
                        }
                    });
                }
            }
        } catch (Exception e) {
            LOG.debug("变更检测失败: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 如果分析过期则自动刷新。
     *
     * @return true 如果执行了刷新
     */
    public boolean refreshIfStale() {
        if (isStale()) {
            LOG.info("检测到代码变更，自动刷新模式分析");
            cachedCodebaseReport = learnFromCodebase();
            lastScanTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    /**
     * 获取代码分析报告（支持缓存）。
     */
    public String getCodebaseReport() {
        refreshIfStale();
        return cachedCodebaseReport != null ? cachedCodebaseReport : learnFromCodebase();
    }

    /**
     * 分析 Git 提交历史中的修改模式。
     *
     * @param limit 分析最近 N 条提交
     * @return Markdown 格式的分析报告
     */
    public String learnFromGitHistory(int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Git 提交历史分析 (最近 ").append(limit).append(" 次)\n\n");

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "git", "log", "-" + limit, "--stat", "--oneline"
            );
            pb.directory(Path.of(System.getProperty("user.dir")).toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                Map<String, Integer> fileChangeCount = new HashMap<>();
                List<String> recentCommits = new ArrayList<>();

                while ((line = reader.readLine()) != null) {
                    if (line.matches("^[0-9a-f]+ .*")) {
                        recentCommits.add(line);
                    }
                    // 统计文件变更: "* file.java | N ..."
                    if (line.contains("|") && line.contains(" ")) {
                        String[] parts = line.split("\\|");
                        if (parts.length >= 1) {
                            String fileName = parts[0].trim();
                            if (!fileName.isBlank()) {
                                fileChangeCount.merge(fileName, 1, Integer::sum);
                            }
                        }
                    }
                }
                process.waitFor();

                if (!recentCommits.isEmpty()) {
                    sb.append("### 最近提交\n\n");
                    for (int i = 0; i < Math.min(5, recentCommits.size()); i++) {
                        sb.append("- ").append(recentCommits.get(i)).append("\n");
                    }
                    sb.append("\n");
                }

                if (!fileChangeCount.isEmpty()) {
                    sb.append("### 热点文件 (修改次数 Top 5)\n\n");
                    sb.append("| 文件 | 修改次数 |\n");
                    sb.append("|------|----------|\n");
                    fileChangeCount.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(5)
                        .forEach(entry -> sb.append("| ").append(entry.getKey())
                            .append(" | ").append(entry.getValue()).append(" |\n"));
                }
            }
        } catch (Exception e) {
            sb.append("Git 分析失败: ").append(e.getMessage()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 学习当前代码库的结构模式。
     *
     * @return Markdown 格式的模式报告
     */
    public String learnFromCodebase() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 项目代码结构分析\n\n");

        Path srcDir = Path.of(System.getProperty("user.dir"), "src", "main", "java", "com", "moma");

        try {
            Map<String, List<String>> packageFiles = new HashMap<>();
            try (Stream<Path> files = Files.walk(srcDir).filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))) {

                files.forEach(p -> {
                    String packageName = srcDir.relativize(p.getParent()).toString()
                        .replace(FileSystems.getDefault().getSeparator(), ".");
                    if (packageName.isEmpty()) packageName = "(root)";
                    packageFiles.computeIfAbsent(packageName, k -> new ArrayList<>())
                        .add(p.getFileName().toString());
                });
            }

            sb.append("### 包结构\n\n");
            sb.append("| 包 | 文件数 | 主要类型 |\n");
            sb.append("|-----|--------|----------|\n");

            // 按包名排序
            packageFiles.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    List<String> files = entry.getValue();
                    String types = summarizePackage(entry.getKey(), files);
                    sb.append("| ").append(entry.getKey()).append(" | ")
                        .append(files.size()).append(" | ")
                        .append(types).append(" |\n");
                });

            sb.append("\n### 编码模式总结\n\n");
            sb.append("- **工具模式**: 实现 `Tool<I,O>` 接口，使用 `record Input()` 定义参数\n");
            sb.append("- **DI 注册**: `@Configuration` + `@Bean` 注解，在 `DiConfig` 集中管理\n");
            sb.append("- **控制器模式**: `extends CommandController(\"prefix\")` + `registerHandlers()`\n");
            sb.append("- **组件注册**: `@Component` 自动扫描 + `@Inject` 构造器注入\n");
            sb.append("- **测试模式**: JUnit 5，测试类命名 `ClassNameTest`\n");

        } catch (Exception e) {
            sb.append("代码分析失败: ").append(e.getMessage()).append("\n");
        }

        // 更新缓存
        String report = sb.toString();
        cachedCodebaseReport = report;
        lastScanTime = System.currentTimeMillis();
        return report;
    }

    /**
     * 综合 git 历史和代码结构分析。
     */
    public String summarize() {
        StringBuilder sb = new StringBuilder();
        sb.append("# MoMa 项目综合分析报告\n\n");
        sb.append(learnFromCodebase());
        sb.append("\n");
        sb.append(learnFromGitHistory(20));
        return sb.toString();
    }

    /**
     * 根据文件名推断包的功能。
     */
    private String summarizePackage(String packageName, List<String> files) {
        if (packageName.contains("tool")) return "工具实现";
        if (packageName.contains("di")) return "DI 容器";
        if (packageName.contains("config")) return "配置管理";
        if (packageName.contains("agent")) return "Agent 循环";
        if (packageName.contains("cli")) return "CLI 交互";
        if (packageName.contains("controller")) return "命令控制器";
        if (packageName.contains("service")) return "服务层";
        if (packageName.contains("repository")) return "持久化";
        if (packageName.contains("memory")) return "记忆系统";
        if (packageName.contains("cache")) return "缓存系统";
        if (packageName.contains("concurrent")) return "并发/事件";
        if (packageName.contains("context")) return "上下文管理";
        if (packageName.contains("model")) return "模型 Provider";
        if (packageName.contains("plan")) return "计划模式";
        if (packageName.contains("skill")) return "技能系统";
        if (packageName.contains("security")) return "安全引擎";
        if (packageName.contains("lsp")) return "LSP 客户端";
        if (packageName.contains("task")) return "任务系统";
        if (packageName.contains("learning")) return "自我学习";
        return files.stream()
            .map(f -> f.replace(".java", ""))
            .limit(3)
            .collect(Collectors.joining(", "));
    }
}

package com.codeassist.tool;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * GitHub CLI 命令执行的共享方法。
 */
public class GhCommand {

    private GhCommand() {}

    /**
     * 执行 gh 命令并返回输出。
     */
    public static String execGh(List<String> cmd, Path workDir, int maxChars) throws ToolException {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            int exitCode = process.waitFor();

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() + line.length() + 1 > maxChars) {
                    sb.append("... (输出过长，已截断)");
                    break;
                }
                sb.append(line).append("\n");
            }
            reader.close();

            if (exitCode != 0) {
                String output = sb.toString().trim();
                throw new ToolException("gh 命令失败 (退出码 " + exitCode + "):\n" + output);
            }
            return sb.toString().trim();
        } catch (ToolException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolException("gh 命令执行失败: " + e.getMessage());
        }
    }
}

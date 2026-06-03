# 墨码 (MoMa) API 参考

> 版本: 1.0.0-SNAPSHOT | 最后更新: 2026-06-01

## 目录

1. [工具系统 API](#1-工具系统-api)
2. [技能系统 API](#2-技能系统-api)
3. [记忆系统 API](#3-记忆系统-api)
4. [任务系统 API](#4-任务系统-api)
5. [计划模式 API](#5-计划模式-api)
6. [缓存系统 API](#6-缓存系统-api)
7. [并发框架 API](#7-并发框架-api)
8. [安全系统 API](#8-安全系统-api)
9. [Provider 热切换 API](#9-provider-热切换-api)
10. [DI 容器 API](#10-di-容器-api)

---

## 1. 工具系统 API

### 1.1 工具接口 `Tool<I, O>`

**包**: `com.moma.tool.Tool`

工具是 Agent 感知和操作代码库的基本单元。每个工具封装一个原子操作。

```java
public interface Tool<I, O> {

    /** 工具名称（唯一标识，LLM 通过此名称调用） */
    String name();

    /** 工具描述（LLM 理解工具用途） */
    String description();

    /** 输入参数的 JSON Schema */
    String inputSchema();

    /** 执行工具逻辑 */
    O execute(I input, AgentContext context) throws ToolException;

    /** 是否为只读（只读工具可并发执行） */
    boolean isReadOnly();

    /** 是否启用 */
    default boolean isEnabled() { return true; }

    /** 将输入 JSON 字符串解析为输入对象 */
    I parseInput(String jsonInput) throws ToolException;

    /** 将输出对象序列化为 JSON 字符串 */
    String formatOutput(O output);
}
```

### 1.2 工具注册中心 `ToolRegistry`

**包**: `com.moma.tool.ToolRegistry`

```java
// 注册工具
void register(Tool<?, ?> tool);

// 取消注册
void unregister(String name);

// 按名称获取
Tool<?, ?> getTool(String name);

// 获取并执行安全检查
Tool<?, ?> getToolChecked(String name, String inputJson) throws ToolException;

// 获取所有启用的工具
List<Tool<?, ?>> getEnabledTools();

// 转换为 LangChain4j ToolSpecification 列表
List<ToolSpecification> getToolSpecifications();

// 设置安全管理器
void setHardDenyManager(HardDenyManager hardDenyManager);
```

### 1.3 工具结果 `ToolResult`

```java
public record ToolResult(boolean success, String content, long durationMs) {
    static ToolResult success(String content, long durationMs);
    static ToolResult failure(String errorMessage, long durationMs);

    /** 转换为 ToolExecutionResultMessage 的内容 */
    String toMessageContent();
}
```

### 1.4 单个工具 API

#### ReadTool

从文件系统读取文件内容，支持指定行范围。

```json
{
  "file_path": "src/main/java/com/moma/CodeAssistant.java",
  "offset": 10,
  "limit": 50
}
```

- `file_path`: 文件路径（必需）
- `offset`: 起始行号（可选，默认 0）
- `limit`: 读取行数（可选，默认全部）

#### WriteTool

写入或覆盖文件内容。

```json
{
  "file_path": "src/main/java/com/moma/Example.java",
  "content": "package com.moma;\n\npublic class Example { }"
}
```

- `file_path`: 文件路径（必需）
- `content`: 文件内容（必需）

#### GlobTool

按通配符模式搜索文件。

```json
{
  "pattern": "**/*.java",
  "path": "src/main/java/com/moma",
  "limit": 100,
  "offset": 0
}
```

- `pattern`: 通配符模式（必需）
- `path`: 搜索目录（可选）
- `limit`: 最大返回数（可选，默认 100）
- `offset`: 偏移量（可选）

#### EditTool

精准文本编辑，支持文本匹配替换或行号范围替换。自动创建 `.bak` 备份。

```json
{
  "file_path": "src/main/java/com/moma/Example.java",
  "old_string": "旧文本内容",
  "new_string": "新文本内容",
  "replace_all": false
}
```

- `file_path`: 文件路径（必需）
- `old_string`: 要替换的文本（必需）
- `new_string`: 替换后的文本（必需）
- `replace_all`: 是否替换所有匹配（可选，默认 false）

#### GrepTool

搜索文件内容，支持正则表达式和文件过滤。

```json
{
  "pattern": "@Component",
  "path": "src/main/java/com/moma",
  "glob": "*.java",
  "output_mode": "content",
  "-n": true,
  "-i": false,
  "head_limit": 50,
  "context": 2
}
```

- `pattern`: 搜索模式（必需）
- `path`: 搜索目录（可选）
- `glob`: 文件过滤（可选）
- `output_mode`: "content" | "files_with_matches" | "count"
- `-n`: 显示行号（可选）
- `-i`: 忽略大小写（可选）
- `head_limit`: 结果数量限制（可选）
- `context`: 上下文字数（可选）

#### BashTool

执行 Shell 命令。

```json
{
  "command": "mvn compile",
  "description": "编译项目",
  "timeout": 120000
}
```

- `command`: Shell 命令（必需）
- `description`: 命令描述（可选）
- `timeout`: 超时毫秒数（可选，默认 120000）

#### LspTool

启动语言服务器并获取代码诊断信息。

```json
{
  "filePath": "src/main/java/com/moma/CodeAssistant.java"
}
```

- `filePath`: 文件路径（必需）

支持的文件类型：
- `.java`: jdtls (Eclipse JDT Language Server)
- `.ts`/`.js`: typescript-language-server
- `.py`: pylsp

#### HtmlOutputTool

生成带样式的 HTML 报告并自动在浏览器打开。

```json
{
  "title": "代码审查报告",
  "bodyHtml": "<h2>审查结果</h2>...",
  "filename": "review-report"
}
```

- `title`: 页面标题（必需）
- `bodyHtml`: HTML 内容（必需，支持表格/徽章/代码高亮）
- `filename`: 文件名（可选）

输出目录: `.ca-html-output/`

#### GitDiffTool

查看 Git 差异。

```json
{
  "path": "src/main/java/com/moma/CodeAssistant.java",
  "commit": "HEAD~3..HEAD"
}
```

- `path`: 限定路径（可选）
- `commit`: 提交范围（可选，如 `HEAD~3..HEAD`）

#### GitStatusTool

查看 Git 工作区状态。无参数。

```json
{}
```

#### GitCommitTool

暂存变更并创建 Git 提交。

```json
{
  "message": "修复登录验证 bug",
  "all": true
}
```

- `message`: 提交信息（必需）
- `all`: 是否暂存所有变更（可选，默认 false）

#### GhPrCreateTool / GhPrListTool / GhIssueListTool

GitHub CLI 工具，需要安装 `gh` 命令行工具。

`GhPrCreateTool`:
```json
{
  "title": "修复登录验证",
  "body": "## 变更内容\n...",
  "head": "fix-login",
  "base": "main"
}
```

`GhPrListTool`:
```json
{
  "state": "open",
  "limit": 10
}
```

`GhIssueListTool`:
```json
{
  "state": "open",
  "limit": 10
}
```

---

## 2. 技能系统 API

### 2.1 技能模型 `Skill`

**包**: `com.moma.skill.Skill`

```java
public record Skill(
    String name,              // 技能名称
    String description,       // 技能描述
    String prompt,            // 注入到系统提示词的指引文本
    List<String> allowedTools // 允许使用的工具白名单
)
```

### 2.2 技能管理器 `SkillManager`

**包**: `com.moma.skill.SkillManager`

```java
// 注册技能
void register(Skill skill);

// 按名称查找
Optional<Skill> getSkill(String name);

// 获取所有技能
List<Skill> getAllSkills();
```

### 2.3 技能工具 `SkillTool`

Agent 通过此工具激活技能。激活后，非白名单中的工具被禁用，系统提示词中注入技能指引。

```json
{
  "name": "code-review"
}
```

内置 4 个技能：

| 技能名 | 描述 | 允许工具 |
|--------|------|---------|
| `code-review` | 审查代码质量、安全性和最佳实践 | Read, Glob, Grep, GitDiff, GitStatus |
| `test-generation` | 为代码生成单元测试 | Read, Glob, Write, Edit |
| `refactoring` | 优化结构而不改变外部行为 | Read, Write, Edit, Glob, Grep, Bash, Git* |
| `bug-fix` | 定位并修复代码缺陷 | Read, Glob, Grep, Edit, GitDiff, GitStatus |

---

## 3. 记忆系统 API

### 3.1 记忆模型 `MemoryEntry`

**包**: `com.moma.memory.MemoryEntry`

```java
public enum Type {
    FACT,        // 事实
    PREFERENCE,  // 偏好
    DECISION,    // 决策
    REFERENCE    // 参考
}

public class MemoryEntry {
    String getId();       // UUID
    Type getType();       // 记忆类型
    String getContent();  // 记忆内容
    String getProject();  // 关联项目
    String getTags();     // 逗号分隔的标签
    long getCreatedAt();  // 创建时间戳
    long getAccessedAt(); // 最后访问时间
    void markAccessed();  // 标记为已访问
}
```

### 3.2 记忆存储器 `MemoryStore`

**包**: `com.moma.memory.MemoryStore`

```java
// 保存记忆
MemoryEntry save(Type type, String content, String project, String tags);

// 按 ID 获取
Optional<MemoryEntry> get(String id);

// 搜索记忆（按项目/标签/关键词）
List<MemoryEntry> search(String project, String tagQuery, String keyword, int limit);

// 删除
boolean delete(String id);

// 获取当前项目记忆
List<MemoryEntry> getProjectMemories(String project, int limit);

// 计数
int size();
```

### 3.3 Agent 工具接口

**MemorySaveTool**:
```json
{
  "type": "fact",
  "content": "用户偏好使用 DeepSeek 作为默认模型",
  "project": "moma",
  "tags": "preference, model"
}
```

**MemorySearchTool**:
```json
{
  "keyword": "DeepSeek",
  "type": "preference",
  "tags": "",
  "limit": 10
}
```

存储位置: `~/.ca/memory/entries.json`，上限 200 条，LRU 淘汰。

### 3.4 Repository 层 `MemoryRepository`

**包**: `com.moma.repository.MemoryRepository`

包装 `MemoryStore` 提供 Repository 模式接口：

```java
MemoryEntry save(MemoryEntry.Type type, String content, String project, String tags);
Optional<MemoryEntry> findById(String id);
List<MemoryEntry> search(String project, String tagQuery, String keyword, int limit);
List<MemoryEntry> findByProject(String project, int limit);
boolean deleteById(String id);
int count();
```

---

## 4. 任务系统 API

### 4.1 任务模型 `Task`

**包**: `com.moma.task.Task`

```java
public enum TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

public record Task(
    String id,
    String description,
    TaskStatus status,
    List<String> dependencies,   // 依赖的任务 ID 列表
    String result,
    long createdAt,
    long updatedAt
)
```

### 4.2 任务管理器 `TaskManager`

**包**: `com.moma.task.TaskManager`

```java
// 创建任务
Task createTask(String description, List<String> dependencies);

// 更新任务状态
void updateTask(String taskId, TaskStatus status, String result);

// 获取任务
Optional<Task> getTask(String taskId);

// 列出所有任务
List<Task> listTasks();

// 清空
void clear();

// 获取下一个可执行的任务（依赖已满足）
Optional<Task> findNextReady();
```

### 4.3 Agent 工具接口

**TaskCreateTool**:
```json
{
  "subject": "读取 UserService.java",
  "description": "了解当前 UserService 的实现",
  "dependencies": []
}
```

**TaskListTool**:
```json
{ }
```

**TaskUpdateTool**:
```json
{
  "taskId": "uuid-here",
  "status": "completed",
  "result": "任务执行成功"
}
```

**TaskGetTool**:
```json
{
  "taskId": "uuid-here"
}
```

任务持久化在 `~/.ca/tasks/tasks-{sessionId}.json`。

---

## 5. 计划模式 API

### 5.1 计划管理器 `PlanManager`

**包**: `com.moma.plan.PlanManager`

```java
public class PlanManager {
    boolean isPlanMode();
    void enterPlanMode();
    void exitPlanMode();
}
```

### 5.2 Agent 工具接口

**EnterPlanModeTool**: 无参数，Agent 调用后进入计划模式。

**ExitPlanModeTool**: 无参数，Agent 调用后退出计划模式。

### 5.3 系统提示词影响

计划模式下，`SystemPrompt.build()` 在提示词中注入计划模式指引：

```
## 计划模式

你当前处于【计划模式】。在此模式下：
1. 当用户给你一个复杂请求时，先输出一个详细的执行计划
2. 计划应包含步骤列表，每个步骤说明做什么、用什么工具、预期结果
3. 然后使用 TaskCreate 为每个步骤创建任务（可设置依赖关系）
4. 等待用户确认后，开始逐步执行每个任务
5. 每完成一个步骤，使用 TaskUpdate 更新任务状态
```

---

## 6. 缓存系统 API

### 6.1 缓存接口 `CacheManager`

**包**: `com.moma.cache.CacheManager`

```java
public interface CacheManager {
    <T> T get(String key, Class<T> type);
    void set(String key, Object value, long ttlSeconds);
    void delete(String key);
    void clear();
    boolean isAvailable();
    CacheStats getStats();

    record CacheStats(long hitCount, long missCount, long size) {}
}
```

### 6.2 本地缓存 `LocalCacheManager`

基于 `ConcurrentHashMap` + 惰性过期。

- 默认 TTL: 300 秒
- 最大条目: 1000
- 超出上限时：清空全部（简化策略）
- 始终可用 (`isAvailable() == true`)

### 6.3 Redis 缓存 `RedisCacheManager`

基于 Jedis 连接池。

- 连接失败时抛出异常 → `CacheConfig` 捕获并降级到本地缓存
- 配置参数：host, port, password, timeout, maxTotal, maxIdle

### 6.4 缓存切面 `CacheAspect`

提供环绕缓存方法：

```java
// 环绕：先查缓存 → 未命中执行 supplier → 写入缓存
<T> T around(String key, Supplier<T> supplier, Class<T> type, long ttl);
```

---

## 7. 并发框架 API

### 7.1 线程池管理器 `ThreadPoolManager`

**包**: `com.moma.concurrent.ThreadPoolManager`

```java
// 提交 Callable 任务到指定线程池
<T> Future<T> submit(Callable<T> task, String poolName);

// 提交 Runnable 任务
Future<?> submit(Runnable task, String poolName);

// 获取指定线程池的 Executor
Executor getExecutor(String poolName);

// 优雅关闭所有线程池
void shutdown();

// 获取所有线程池的统计信息
Map<String, PoolStats> getStats();
```

可用线程池名: `compute`, `io`, `cache`, `event`

`PoolStats`:
```java
record PoolStats(int poolSize, int activeCount, int queueDepth,
                 long completedTasks, long totalTasks)
```

### 7.2 异步执行器 `AsyncExecutor`

```java
// 提交异步任务
<T> CompletableFuture<T> submit(Callable<T> task, String poolName);

// 带超时提交
<T> CompletableFuture<T> submit(Callable<T> task, String poolName, Duration timeout);

// 异步执行（无返回值）
CompletableFuture<Void> runAsync(Runnable task, String poolName);
```

### 7.3 事件总线 `EventBus`

```java
// 订阅事件
<T> void subscribe(Class<T> eventType, Consumer<T> handler);

// 同步发布
<T> void publish(T event);

// 异步发布
<T> void publishAsync(T event, Executor executor);

// 清空订阅
void clear();

// 订阅者数量
int subscriberCount();
```

---

## 8. 安全系统 API

### 8.1 HardDenyManager

**包**: `com.moma.security.HardDenyManager`

```java
// 添加规则（格式: "ToolName(pattern)"）
void addRule(String ruleStr);

// 批量添加
void addRules(List<String> ruleStrs);

// 检查工具是否被禁止（未禁止返回 null）
String checkTool(Tool<?, ?> tool, String inputJson);

// 检查并抛出异常
void enforce(Tool<?, ?> tool, String inputJson) throws ToolException;

// 添加 9 条默认规则
void addDefaultRules();

// 获取所有规则
List<HardDenyRule> getRules();
```

### 8.2 规则模型 `HardDenyRule`

**包**: `com.moma.security.HardDenyRule`

规则格式: `"ToolName(pattern)"` 或 `"ToolName(*)"`（禁止所有调用）

示例:
```
Bash(rm -rf /)           → 禁止 rm -rf /
Bash(git push --force)   → 禁止强制推送
WebFetch                 → 禁止所有 WebFetch 调用
FileWrite(*.env)         → 禁止写入 .env 文件
```

---

## 9. Provider 热切换 API

### 9.1 Provider 接口 `ModelProvider`

**包**: `com.moma.model.ModelProvider`

```java
public interface ModelProvider {
    String name();
    ChatLanguageModel createModel(ModelConfig config);
    List<String> supportedModels();
    boolean isAvailable();

    @lombok.Builder
    record ModelConfig(
        String baseUrl,
        String apiKey,
        String modelName,
        double temperature,
        int maxTokens,
        int timeoutSeconds
    )
}
```

### 9.2 Provider 注册中心 `ProviderRegistry`

```java
// 注册 Provider
void register(ModelProvider provider);

// 从 settings.json 加载配置
void loadConfigs(Map<String, ProviderConfig> configs);

// 切换 Provider 和模型
String switchTo(String providerName, String modelName);

// 初始化默认模型
void initDefault(ChatLanguageModel model, String providerName, String modelName);

// 查询
ChatLanguageModel getCurrentModel();
String getActiveProvider();
String getActiveModel();
Set<String> getProviderNames();
String describeProvider(String name);
```

### 9.3 内置 Provider

**OpenAiProvider**: 兼容所有 OpenAI 协议的服务（OpenAI、DeepSeek、Kiro 等）

---

## 10. DI 容器 API

### 10.1 ApplicationContext

**包**: `com.moma.di.ApplicationContext`

```java
// 注册 Bean 定义
ApplicationContext register(BeanDefinition definition);
ApplicationContext register(Class<?> clazz);
ApplicationContext registerPackage(String... basePackages);

// 设置属性源（用于 @Value 解析）
ApplicationContext setPropertySource(Map<String, String> propertySource);

// 刷新容器（初始化所有单例 Bean）
void refresh();

// 获取 Bean
<T> T getBean(String name);
<T> T getBean(Class<T> type);
<T> Optional<T> getBeanOptional(Class<T> type);

// 查询
boolean containsBean(String name);
Set<String> getBeanNames();
Set<String> getSingletonBeanNames();
boolean isRefreshed();
```

### 10.2 支持的注解

| 注解 | 包路径 | 目标 |
|------|--------|------|
| `@Component` | `com.moma.di.Component` | 类 |
| `@Configuration` | `com.moma.di.Configuration` | 类 |
| `@Bean` | `com.moma.di.Bean` | 方法 |
| `@Inject` | `com.moma.di.Inject` | 构造器/字段/方法 |
| `@Value` | `com.moma.di.Value` | 字段/参数 |
| `@Primary` | `com.moma.di.Primary` | 类 |
| `@PostConstruct` | `com.moma.di.PostConstruct` | 方法 |

### 10.3 Bean 定义 `BeanDefinition`

```java
@lombok.Builder
public class BeanDefinition {
    String name;
    Class<?> beanClass;
    String scope;             // "singleton" | "prototype"
    boolean primary;
    String initMethodName;    // @PostConstruct 方法名
    Method factoryMethod;     // @Bean 工厂方法
    Object factoryBeanInstance; // 工厂方法所属的配置类实例

    boolean isSingleton();
}
```

# 墨码 (MoMa) 开发者指南

> 版本: 1.0.0-SNAPSHOT | 最后更新: 2026-06-01

## 目录

1. [环境要求](#1-环境要求)
2. [构建与运行](#2-构建与运行)
3. [项目结构](#3-项目结构)
4. [编码规范](#4-编码规范)
5. [如何扩展](#5-如何扩展)
6. [测试](#6-测试)
7. [提交规范](#7-提交规范)

---

## 1. 环境要求

### 必需

| 工具 | 版本 | 用途 |
|------|------|------|
| JDK | 21+ | 编译和运行（需要虚拟线程支持） |
| Maven | 3.8+ | 项目构建 |
| Git | 任意 | 版本控制 |

### 可选

| 工具 | 用途 |
|------|------|
| Redis | 缓存加速（非必需，会自动降级到本地缓存） |
| jdtls (Eclipse JDT Language Server) | LSP Java 诊断 |
| Ollama | 运行本地模型（如 qwen2.5-coder:7b） |

---

## 2. 构建与运行

### 2.1 快速开始

```bash
# 1. 编译项目
mvn compile

# 2. 运行测试
mvn test

# 3. 打包为可执行 fat JAR
mvn package -DskipTests

# 4. 配置 API Key
cp .env.example .env
# 编辑 .env 填入你的 API Key

# 5. 运行
java -jar target/moma.jar
```

### 2.2 Windows 快捷启动

```bash
start.bat
```

### 2.3 常用 Maven 命令

| 命令 | 说明 |
|------|------|
| `mvn compile` | 编译 |
| `mvn test` | 运行测试 |
| `mvn package -DskipTests` | 打包 fat JAR |
| `mvn clean compile` | 清理并编译 |
| `mvn clean package -DskipTests` | 清理并重新打包 |

### 2.4 IDE 导入

推荐使用 IntelliJ IDEA：
1. `File → Open` 选择项目根目录
2. IDEA 自动识别 Maven 项目
3. 等待依赖下载完成
4. 运行 `com.moma.CodeAssistant` 的 `main()` 方法

---

## 3. 项目结构

```
src/
├── main/
│   ├── java/com/moma/
│   │   ├── CodeAssistant.java      # 主入口
│   │   ├── agent/                  # Agent 核心层
│   │   │   ├── AgentLoop.java      # perceive-think-act 循环
│   │   │   ├── AgentContext.java   # Agent 执行上下文
│   │   │   ├── SystemPrompt.java   # 系统提示词模板
│   │   │   └── SessionManager.java # 会话管理器
│   │   ├── cache/                  # 缓存抽象层
│   │   │   ├── CacheManager.java   # 缓存接口
│   │   │   ├── LocalCacheManager.java # 本地内存缓存
│   │   │   ├── RedisCacheManager.java # Redis 缓存
│   │   │   ├── CacheAspect.java   # 缓存切面
│   │   │   └── Cacheable.java     # 缓存标记接口
│   │   ├── cli/                    # CLI 层
│   │   │   ├── CliApp.java        # JLine REPL
│   │   │   └── CommandParser.java # 斜杠命令解析
│   │   ├── concurrent/            # 并发框架
│   │   │   ├── ThreadPoolManager.java # 线程池管理器
│   │   │   ├── ThreadPoolConfigData.java # 配置数据
│   │   │   ├── AsyncExecutor.java # 异步执行器
│   │   │   ├── AsyncToolExecutor.java # 异步工具执行
│   │   │   └── EventBus.java      # 事件总线
│   │   ├── config/                # 配置层
│   │   │   ├── AppConfig.java     # 配置模型
│   │   │   ├── ConfigLoader.java  # 配置加载器
│   │   │   ├── DiConfig.java      # DI 配置类
│   │   │   ├── CacheConfig.java   # 缓存配置
│   │   │   └── ThreadPoolConfig.java # 线程池配置
│   │   ├── controller/            # 控制器层
│   │   │   ├── CommandController.java # 抽象基类
│   │   │   ├── ProviderController.java # /provider
│   │   │   ├── PlanController.java   # /plan
│   │   │   ├── TaskController.java   # /tasks
│   │   │   ├── MemoryController.java # /memory
│   │   │   └── StatusController.java # /status
│   │   ├── di/                    # DI 容器
│   │   │   ├── ApplicationContext.java # 核心容器
│   │   │   ├── BeanDefinition.java    # Bean 定义
│   │   │   ├── ComponentScanner.java  # 包扫描器
│   │   │   └── 注解定义文件
│   │   ├── lsp/                   # LSP 客户端
│   │   │   └── LspClient.java    # JSON-RPC 实现
│   │   ├── memory/               # 记忆系统
│   │   │   ├── MemoryEntry.java  # 记忆模型
│   │   │   ├── MemoryStore.java  # 持久化存储
│   │   │   ├── MemorySaveTool.java # 保存工具
│   │   │   └── MemorySearchTool.java # 搜索工具
│   │   ├── model/                # AI 模型层
│   │   │   ├── ModelProvider.java # Provider 接口
│   │   │   ├── OpenAiProvider.java # OpenAI 兼容实现
│   │   │   ├── ProviderConfig.java # 配置 POJO
│   │   │   └── ProviderRegistry.java # 注册中心
│   │   ├── plan/                 # 计划模式
│   │   │   ├── PlanManager.java  # 状态管理
│   │   │   ├── EnterPlanModeTool.java
│   │   │   └── ExitPlanModeTool.java
│   │   ├── repository/           # 数据访问层
│   │   │   ├── MemoryRepository.java
│   │   │   ├── SessionRepository.java
│   │   │   └── TaskRepository.java
│   │   ├── security/             # 安全系统
│   │   │   ├── HardDenyManager.java # 安全引擎
│   │   │   └── HardDenyRule.java # 规则模型
│   │   ├── service/              # 服务层
│   │   │   ├── AgentService.java # Agent 服务
│   │   │   ├── ToolOrchestrationService.java # 工具编排
│   │   │   ├── MessageService.java # 消息管理
│   │   │   ├── SessionService.java # 会话服务
│   │   │   └── CacheService.java # 缓存服务
│   │   ├── skill/                # 技能系统
│   │   │   ├── Skill.java       # 技能模型
│   │   │   ├── SkillManager.java # 技能管理器
│   │   │   └── SkillTool.java   # 激活工具
│   │   ├── task/                 # 任务系统
│   │   │   ├── Task.java        # 任务模型
│   │   │   ├── TaskManager.java # 任务管理器
│   │   │   ├── TaskCreateTool.java
│   │   │   ├── TaskListTool.java
│   │   │   ├── TaskUpdateTool.java
│   │   │   └── TaskGetTool.java
│   │   └── tool/                 # 工具系统
│   │       ├── Tool.java        # 工具接口
│   │       ├── ToolRegistry.java # 注册中心
│   │       ├── ToolResult.java  # 执行结果
│   │       ├── ToolException.java
│   │       ├── JsonSchemaParser.java
│   │       ├── ReadTool.java / WriteTool.java
│   │       ├── EditTool.java / GlobTool.java
│   │       ├── GrepTool.java / BashTool.java
│   │       ├── LspTool.java / HtmlOutputTool.java
│   │       ├── GitDiffTool.java / GitStatusTool.java
│   │       ├── GitCommitTool.java
│   │       └── GhPrCreateTool.java / GhPrListTool.java
│   │         └── GhIssueListTool.java / GhCommand.java
│   ├── resources/
│   │   └── logback.xml        # 日志配置
│   └── ...
└── test/
    └── java/com/moma/
        ├── di/ApplicationContextTest.java  # DI 容器测试
        ├── concurrent/ThreadPoolManagerTest.java # 线程池测试
        └── cache/LocalCacheManagerTest.java # 缓存测试
```

---

## 4. 编码规范

### 4.1 Java 风格

- **缩进**: 4 个空格（不使用 Tab）
- **命名**: `camelCase` 方法/变量, `PascalCase` 类名, `UPPER_SNAKE` 常量
- **Javadoc**: 公开 API 需要 Javadoc，内部方法非必需
- **记录**: 使用 `record` 替代简单的 POJO
- **注解**: 优先使用按类型自动注入，避免按名称注入

### 4.2 DI 容器约定

```java
// ✅ 正确：使用 @Component 标记组件
@Component
public class MyService {
    // @Inject 构造器注入
    @Inject
    public MyService(Dependency dep) {
        this.dep = dep;
    }
}

// ✅ 正确：使用 @Configuration + @Bean
@Configuration
public class MyConfig {
    @Bean
    public MyService myService() {
        return new MyService(...);
    }
}

// ✅ 正确：使用 @Value 注入配置
@Value("${redis.host:localhost}")
private String redisHost;

// ✅ 正确：使用 @PostConstruct 初始化
@PostConstruct
public void init() {
    // 初始化逻辑
}
```

### 4.3 工具实现规范

```java
// 1. 实现 Tool<I, O> 接口
public class MyTool implements Tool<MyInput, MyOutput> {

    @Override
    public String name() { return "my-tool"; }

    @Override
    public String description() { return "我的工具"; }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "param1": { "type": "string", "description": "参数1" }
            },
            "required": ["param1"]
        }
        """;
    }

    @Override
    public MyInput parseInput(String jsonInput) {
        // 使用 Jackson/ObjectMapper 解析
    }

    @Override
    public MyOutput execute(MyInput input, AgentContext context) {
        // 实现核心逻辑
    }

    @Override
    public String formatOutput(MyOutput output) {
        // 序列化为 JSON
    }

    @Override
    public boolean isReadOnly() { return true; } // 只读可并发
}

// 2. 在 DiConfig 中注册
registry.register(new MyTool());
```

---

## 5. 如何扩展

### 5.1 添加新工具

1. 在 `src/main/java/com/moma/tool/` 下创建 `XxxTool.java`
2. 实现 `Tool<I, O>` 接口
3. 在 `DiConfig.toolRegistry()` 中注册 `registry.register(new XxxTool())`
4. 在 `SystemPrompt.java` 中更新工具列表（可选）

### 5.2 添加新技能

1. 在 `SkillManager.registerBuiltinSkills()` 中添加：
```java
register(new Skill("my-skill",
    "技能描述",
    "## 技能指引文本..."
    List.of("Read", "Write", "Edit")));
```

### 5.3 添加新命令

1. 在 `controller/` 下创建 `XxxController.java`，继承 `CommandController`
2. 实现 `registerHandlers()` 方法
3. 在 `DiConfig.commandControllers()` 中添加构造参数

### 5.4 添加新 Provider

1. 在 `model/` 下创建 `XxxProvider.java`，实现 `ModelProvider` 接口
2. 在 `DiConfig.providerRegistry()` 中调用 `registry.register(new XxxProvider())`
3. 在 `settings.json` 的 `providers` 块中添加配置

### 5.5 添加新 Repository

1. 在 `repository/` 下创建 `XxxRepository.java`，标注 `@Component`
2. 注入底层数据源
3. 在 `DiConfig` 中通过自动注入使用

---

## 6. 测试

### 6.1 测试框架

- JUnit 5 (`org.junit.jupiter`)
- 测试文件位置：`src/test/java/com/moma/`

### 6.2 运行测试

```bash
mvn test
```

### 6.3 测试用例

| 测试文件 | 测试用例数 | 覆盖内容 |
|---------|-----------|---------|
| `ApplicationContextTest.java` | 18 | 组件注册、依赖注入、循环依赖检测、@Value 解析等 |
| `ThreadPoolManagerTest.java` | 11 | 线程池提交/执行/统计/关闭 |
| `LocalCacheManagerTest.java` | - | 本地缓存功能 |

### 6.4 编写测试原则

- 每个测试只测试一个关注点
- 测试命名：`should_xxx_when_xxx`
- 对 DI 容器：验证 Bean 创建和注入是否正确
- 对工具：验证参数解析、执行逻辑、异常处理

---

## 7. 提交规范

### 7.1 Commit Message 格式

```
<type>: <简短描述>

<详细说明（可选）>
```

**type 类型**：
- `feat` — 新功能
- `fix` — Bug 修复
- `refactor` — 重构
- `test` — 测试
- `docs` — 文档
- `chore` — 构建/杂项

### 7.2 提交示例

```
feat: 添加 Redis 缓存支持

实现 CacheManager 接口的 Redis 实现，
使用 Jedis 连接池管理连接。
连接失败时自动降级到本地缓存。
```

```
refactor: 项目更名为墨码 (MoMa)

重构为 DI + Redis + 并发架构，
包名从 com.codeassist 改为 com.moma。
```

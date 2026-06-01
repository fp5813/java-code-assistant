<div align="center">

```
 __     __   __     ______        __          __        ______   __     __   __     ______   __     __   __     __   __    
/\ \   /\ "-.\ \   /\  ___\      /\ \        /\ \      /\  __ \ /\ \   /\ "-.\ \   /\  ___\ /\ \   /\ \ /\ \   /\ "-.\ \   
\ \ \  \ \ \-.  \  \ \  __\      \ \ \____   \ \ \     \ \  __ \\ \ \  \ \ \-.  \  \ \  __\ \ \ \  \ \ \\ \ \  \ \ \-.  \  
 \ \_\  \ \_\\"\_\  \ \_____\     \ \_____\   \ \_\     \ \_\ \_\\ \_\  \ \_\\"\_\  \ \_\    \ \_\  \ \_\\ \_\  \ \_\\"\_\ 
  \/_/   \/_/ \/_/   \/_____/      \/_____/    \/_/      \/_/\/_/ \/_/   \/_/ \/_/   \/_/     \/_/   \/_/ \/_/   \/_/ \/_/ 
                                                                                                                           
```

### 墨码 (MoMa) — AI 编程助手

[![Java](https://img.shields.io/badge/Java-21%2B-orange?logo=openjdk)](https://openjdk.org)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-0.36.2-blue?logo=java)](https://github.com/langchain4j/langchain4j)
[![Maven](https://img.shields.io/badge/Maven-3.9%2B-red?logo=apache-maven)](https://maven.apache.org)
[![License](https://img.shields.io/badge/License-MIT-1ba784)](LICENSE)

**中文** | [English](README.md)

</div>

**墨码 (MoMa)** — 以 AI 为笔，挥洒自如地编写代码。基于 **Java 21** + **LangChain4j** 构建的终端 AI 编程助手，具备完整的 **感知-思考-行动 (perceive-think-act)** Agent 闭环，支持多 Provider 热切换，内置 20+ 开发工具。

参考了 Claude Code / MiniClaude 的架构设计。

---

## 快速开始

### 前置要求

- **Java 21+**
- **Maven 3.9+**
- **API Key**（OpenAI、DeepSeek 等）或 **Ollama**（本地模型）

### 构建

```bash
git clone https://github.com/fp5813/java-code-assistant.git && cd java-code-assistant
mvn package -DskipTests
```

### 配置

创建 `~/.ca/settings.json` 或复制 `.env.example` 为 `.env`：

```json
{
  "env": {
    "ANTHROPIC_BASE_URL": "https://api.deepseek.com",
    "ANTHROPIC_AUTH_TOKEN": "sk-your-api-key",
    "ANTHROPIC_MODEL": "deepseek-chat"
  },
  "model": "deepseek-chat"
}
```

使用 **Ollama** 本地模型：

```env
ANTHROPIC_BASE_URL=http://localhost:11434/v1
ANTHROPIC_AUTH_TOKEN=ollama
ANTHROPIC_MODEL=qwen2.5-coder:7b
```

### 启动

```bash
java -jar target/moma.jar
# Windows: 双击 start.bat
```

---

## 功能总览

| 分类 | 功能 |
|------|------|
| **🤖 Agent 循环** | perceive-think-act 闭环，工具编排（只读并发，写入串行） |
| **📂 文件操作** | Read（行范围）、Write、Glob（通配符）、Edit（文本/行号 + .bak 备份） |
| **🔍 内容搜索** | Grep（正则 + 文件过滤）、Glob（路径模式） |
| **⚡ Shell 执行** | Bash（超时控制 + 输出截断 + hard_deny 安全） |
| **🔄 多 Provider** | 运行时热切换 OpenAI / DeepSeek / Ollama |
| **📋 Git 集成** | Diff 查看、状态查看、提交 |
| **📊 报告输出** | HTML 报告生成 + 自动浏览器打开 |
| **🔬 LSP 诊断** | 通过语言服务器协议分析代码错误/警告 |
| **📌 任务系统** | 创建/查看/更新子任务，支持依赖关系 |
| **📝 计划模式** | Agent 先规划后执行（`/plan`） |
| **🧠 记忆系统** | 跨会话保存和检索重要信息 |
| **🎯 技能系统** | 代码审查、测试生成、重构、Bug 修复 |
| **🛡️ 安全系统** | HardDeny 规则引擎，集中拦截危险命令 |

---

## 命令参考

| 命令 | 说明 |
|------|------|
| `/help` | 显示帮助 |
| `/status` | 显示当前状态 |
| `/provider` | 列出所有 Provider |
| `/provider <name>` | 运行时切换 Provider |
| `/model <name>` | 当前 Provider 内切换模型 |
| `/plan` | 进入计划模式 |
| `/plan execute` | 退出计划模式 |
| `/tasks` | 查看任务列表 |
| `/tasks clear` | 清除所有任务 |
| `/sessions` | 历史会话列表 |
| `/memory` | 查看记忆 |
| `/memory <keyword>` | 搜索记忆 |
| `/clear` | 清屏 |
| `/exit` | 退出 |

### Provider 热切换示例

```
> /provider                   # 列出所有 Provider
> /provider deepseek          # 切换到 DeepSeek
✓ 已切换到 Provider: deepseek, Model: deepseek-chat

> /model gpt-4o-mini          # 切换模型
✓ 已切换到 Model: gpt-4o-mini
```

---

## 架构

```
┌──────────────────────────────────────────────────┐
│                CLI / REPL (JLine)                 │
├──────────────────────────────────────────────────┤
│              Agent Loop (感知-思考-行动)           │
│                                                   │
│  ┌─────────┐   ┌─────────┐   ┌──────────┐       │
│  │  感知    │   │  思考   │   │  行动    │       │
│  │ 用户输入 │──▶│ LLM 调用│──▶│ 工具执行  │──┐    │
│  │  上下文  │   │ + 工具  │   │ (21个)   │  │    │
│  └─────────┘   └─────────┘   └──────────┘  │    │
│       │              ▲             │        │    │
│       └──────────────┴─────────────┘────────┘    │
│                     (循环直到完成)                 │
├──────────────────────────────────────────────────┤
│  工具: Read/Write/Edit/Glob/Grep/Bash/Git/Task   │
│  Provider: OpenAI/DeepSeek/Ollama (热切换)        │
│  安全: HardDeny 规则引擎                          │
│  记忆: 跨会话持久化                               │
└──────────────────────────────────────────────────┘
```

---

## 开发

```bash
mvn clean compile                      # 编译
mvn package -DskipTests                # 打包 fat JAR
java -jar target/moma.jar  # 运行
```

---

## 许可证

**MIT License** © 2026

基于 [LangChain4j](https://github.com/langchain4j/langchain4j) 构建，参考 [MiniClaude](https://github.com/txl16095/MiniClaude) 和 [Claude Code](https://docs.anthropic.com/en/docs/claude-code) 的架构设计。

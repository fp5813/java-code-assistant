<div align="center">

```
 __     __   __     ______        __          __        ______   __     __   __     ______   __     __   __     __   __    
/\ \   /\ "-.\ \   /\  ___\      /\ \        /\ \      /\  __ \ /\ \   /\ "-.\ \   /\  ___\ /\ \   /\ \ /\ \   /\ "-.\ \   
\ \ \  \ \ \-.  \  \ \  __\      \ \ \____   \ \ \     \ \  __ \\ \ \  \ \ \-.  \  \ \  __\ \ \ \  \ \ \\ \ \  \ \ \-.  \  
 \ \_\  \ \_\\"\_\  \ \_____\     \ \_____\   \ \_\     \ \_\ \_\\ \_\  \ \_\\"\_\  \ \_\    \ \_\  \ \_\\ \_\  \ \_\\"\_\ 
  \/_/   \/_/ \/_/   \/_____/      \/_____/    \/_/      \/_/\/_/ \/_/   \/_/ \/_/   \/_/     \/_/   \/_/ \/_/   \/_/ \/_/ 
                                                                                                                           
```

### 墨码 (MoMa) — AI Coding Assistant

[![Java](https://img.shields.io/badge/Java-21%2B-orange?logo=openjdk)](https://openjdk.org)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-0.36.2-blue?logo=java)](https://github.com/langchain4j/langchain4j)
[![Maven](https://img.shields.io/badge/Maven-3.9%2B-red?logo=apache-maven)](https://maven.apache.org)
[![License](https://img.shields.io/badge/License-MIT-1ba784)](LICENSE)

[中文](README_ZH.md) | **English**

</div>

**墨码 (MoMa)** — Write code with AI as your brush. An AI-powered terminal coding assistant built with **Java 21** and **LangChain4j** — featuring a full **perceive-think-act** agent loop, multi-provider hot-switching, and 20+ developer tools.

Inspired by the architecture of Claude Code / MiniClaude.

---

## Quick Start

### Prerequisites

- **Java 21+** ([OpenJDK](https://openjdk.org))
- **Maven 3.9+** (or use `mvnw`)
- An **API key** (OpenAI, DeepSeek, or any OpenAI-compatible API) or **Ollama** for local models

### Build & Run

```bash
git clone https://github.com/fp5813/java-code-assistant.git && cd java-code-assistant
mvn package -DskipTests
```

#### Configuration

Create `~/.ca/settings.json` or copy `.env.example` to `.env`:

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

For **Ollama** (local):

```env
ANTHROPIC_BASE_URL=http://localhost:11434/v1
ANTHROPIC_AUTH_TOKEN=ollama
ANTHROPIC_MODEL=qwen2.5-coder:7b
```

#### Start the REPL

```bash
java -jar target/moma.jar
# Or on Windows: double-click start.bat
```

---

## Features

| Category | Tools & Capabilities |
|----------|---------------------|
| **🤖 Agent Loop** | perceive-think-act cycle, tool orchestration (concurrent reads, serial writes) |
| **📂 File Tools** | Read (line range), Write, Glob (wildcard search), Edit (text/line mode + .bak) |
| **🔍 Search** | Grep (regex + file filter), Glob (path pattern) |
| **⚡ Shell** | Bash execution (timeout + output truncation) |
| **🔄 Multi‑Provider** | Hot-switch between OpenAI, DeepSeek, local Ollama at runtime (`/provider`) |
| **📋 Git Integration** | GitDiff, GitStatus, GitCommit |
| **📊 Reports** | HTML output with auto browser open |
| **🔬 LSP Diagnostics** | Code analysis via Language Server Protocol |
| **📌 Task System** | Create, list, update, track sub-tasks with dependencies |
| **📝 Planning Mode** | Agent plans before execution (`/plan`) |
| **🧠 Memory System** | Cross-session memory (save/search facts, decisions) |
| **🎯 Skills** | Code review, test generation, refactoring, bug-fix |
| **🛡️ Security** | HardDeny rule engine (blocks dangerous commands centrally) |

### Available Commands

| Command | Description |
|---------|-------------|
| `/help` | Show help message |
| `/status` | Display current state (provider, model, tools, stats) |
| `/provider` | List all available providers |
| `/provider <name>` | Switch to a different provider at runtime |
| `/model <name>` | Switch model within the current provider |
| `/plan` | Enter planning mode |
| `/plan execute` | Exit planning mode |
| `/tasks` | Show task list |
| `/tasks clear` | Clear all tasks |
| `/sessions` | Show conversation history |
| `/memory` | Show saved memories |
| `/memory <keyword>` | Search memories |
| `/clear` | Clear screen |
| `/exit` | Exit |

### Multi-Provider Configuration

Define multiple providers in `settings.json`:

```json
{
  "providers": {
    "openai": {
      "baseUrl": "https://api.openai.com/v1",
      "apiKey": "sk-...",
      "models": ["gpt-4o", "gpt-4o-mini"],
      "description": "OpenAI API"
    },
    "deepseek": {
      "baseUrl": "https://api.deepseek.com",
      "apiKey": "sk-...",
      "models": ["deepseek-chat", "deepseek-coder"],
      "description": "DeepSeek API"
    },
    "ollama": {
      "baseUrl": "http://localhost:11434/v1",
      "apiKey": "ollama",
      "models": ["qwen2.5-coder:7b", "qwen2.5-coder:0.5b"],
      "description": "Local Ollama"
    }
  },
  "model": "deepseek-chat"
}
```

Switch at runtime without restart:

```
> /provider deepseek
✓ Switched to Provider: deepseek, Model: deepseek-chat

> /model gpt-4o-mini
✓ Switched to Model: gpt-4o-mini
```

---

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                    CLI / REPL (JLine)                     │
│              ┌──────────────────────────┐                │
│              │   CommandParser          │                │
│              │   /help /provider /plan   │                │
│              └──────────┬───────────────┘                │
└─────────────────────────┼────────────────────────────────┘
                          │
┌─────────────────────────▼────────────────────────────────┐
│              Agent Loop (perceive-think-act)              │
│                                                          │
│  ┌─────────┐    ┌─────────┐    ┌──────────┐             │
│  │PERCEIVE │    │ THINK   │    │   ACT    │             │
│  │ User In │───▶│ LLM Call│───▶│ Tool Exec│──┐          │
│  │ Context │    │ + Tools │    │ (20+)    │  │          │
│  └─────────┘    └─────────┘    └──────────┘  │          │
│       │              ▲              │         │          │
│       └──────────────┴──────────────┘─────────┘          │
│                     (loop with tool results)              │
└──────────────────────────────────────────────────────────┘
         │              │              │
         ▼              ▼              ▼
   ┌──────────┐  ┌────────────┐  ┌──────────┐
   │  Tools   │  │  Provider  │  │ Security │
   │ Read     │  │  Registry  │  │ HardDeny │
   │ Write    │  │  OpenAI    │  │ Rules    │
   │ Edit     │  │  DeepSeek  │  └──────────┘
   │ Bash     │  │  Ollama    │
   │ Git*     │  └────────────┘
   │ LSP      │
   │ Task*    │  ┌────────────┐  ┌──────────┐
   │ Skill    │  │  Memory    │  │  Skills  │
   │ HtmlOut  │  │  Store     │  │  review  │
   └──────────┘  └────────────┘  │  test-gen│
                                 │  refactor│
                                 │  bug-fix │
                                 └──────────┘
```

### Project Structure

```
src/main/java/com/moma/
├── CodeAssistant.java          # Main entry
├── cli/                        # REPL + command parsing
├── agent/                      # perceive-think-act loop
├── tool/                       # 25+ tool implementations
├── model/                      # Provider registry + hot-switching
├── config/                     # Settings (env + JSON)
├── di/                         # Custom DI container
├── controller/                 # Command controllers
├── service/                    # Service layer
├── repository/                 # Data persistence
├── cache/                      # Cache system (Local + Redis)
├── concurrent/                 # Thread pools + EventBus
├── context/                    # Context window management
├── security/                   # HardDeny rule engine
├── task/                       # Task management system
├── plan/                       # Planning mode
├── skill/                      # 5 built-in skills
├── memory/                     # Cross-session memory
├── learning/                   # Pattern analysis
├── lsp/                        # LSP client
```

### Agent Loop

```
while (not complete) {
    1. PERCEIVE: collect user input + history + context
    2. THINK: call LLM with messages + available tools
    3. PARSE response:
       a. text → output to user, done
       b. tool_use → ACT
    4. ACT: execute tools
       - Read-only (Read/Glob/Grep): concurrent
       - Write (Write/Edit/Bash/Git): serial
    5. append tool results → continue THINK
}
```

---

## Tools Reference (28 tools)

| Tool | Read-Only | Description |
|------|-----------|-------------|
| Read | ✓ | Read file content (line range) |
| Write | | Write/overwrite file |
| Glob | ✓ | Wildcard file search |
| Edit | | Precise text/line replacement (.bak backup) |
| Grep | ✓ | Regex content search with file filtering |
| Bash | | Shell command execution (timeout + hard_deny) |
| GitDiff | ✓ | View Git diff |
| GitStatus | ✓ | View working tree status |
| GitCommit | | Stage and commit changes |
| TaskCreate | | Create sub-task (with dependencies) |
| TaskList | ✓ | List all tasks |
| TaskUpdate | | Update task status/result |
| TaskGet | ✓ | View task details |
| EnterPlanMode | | Enter planning mode |
| ExitPlanMode | | Exit planning mode |
| Skill | | Activate a skill (5 available) |
| MemorySave | | Save cross-session memory |
| MemorySearch | ✓ | Search saved memories |
| HtmlOutput | | Generate HTML report + browser open |
| Lsp | ✓ | LSP code diagnostics |
| GhPrCreate | | Create GitHub PR |
| GhPrList | ✓ | List GitHub PRs |
| GhIssueList | ✓ | List GitHub Issues |
| MomaLog | ✓ | Read and analyze MoMa logs |
| MomaMonitor | ✓ | Runtime metrics (tokens, JVM, tools) |
| SaveExperience | | Save development experience to memory |
| PatternLearn | ✓ | Analyze code patterns and git history |
| KnowledgeSearch | ✓ | Search architecture reference knowledge |

---

## Development

```bash
# Build
mvn clean compile
mvn package -DskipTests        # fat JAR

# Run with local Ollama
cp .env.example .env
# edit .env: set ANTHROPIC_BASE_URL=http://localhost:11434/v1
java -jar target/moma.jar

# Run from source
mvn exec:java -Dexec.mainClass="com.moma.CodeAssistant"
```

---

## License

**MIT License** © 2026

Built with [LangChain4j](https://github.com/langchain4j/langchain4j), inspired by  [Claude Code](https://docs.anthropic.com/en/docs/claude-code).

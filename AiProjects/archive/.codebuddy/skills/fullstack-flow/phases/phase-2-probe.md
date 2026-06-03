---
name: fullstack-flow/phases/phase-2-probe
description: "代码探路：并行子代理（codegraph + mysql-archive）探路并生成 L0-L5 结构化探路报告。只读模式。"
version: 2.1.0
tags: [fullstack, codebuddy-only, probe, read-only]
role: codebuddy-prober
model: deepseek-v4-flash
tools: [Read, Grep, Agent]
references:
  - ../references/codegraph-reference.md
  - ../references/api-fetcher-reference.md
  - ../references/mcp-tools-summary.md
mcpServers: [mysql-archive, api-fetcher]
---

# Phase 2: 代码探路

> 由 Phase 1 提供原料。使用 **codegraph** MCP 主力探路，并行子代理提升效率。

## ⚡ 每日启动（开始开发前只做一次）

每次开始开发前，确认 token 有效即可直接使用 `api-fetcher` 调用后端 API：

```
▶ api_login          # 首次需手动调用，token 缓存 6 天
▶ api_list ...       # 之后自动注入 token，无需再登录
```

> 当后端未启动或 token 过期时，`api_list` 会提示 401，只需重新调用 `api_login`。
> `api-fetcher` 服务已在 `.mcp.json` 中注册，无需额外配置。

## 职责

**输入**：Phase 1 澄清后的描述（含关键词、目标模块）  
**输出**：`docs/探路报告/YYYY-MM-DD-{功能简述}.md`  
**模式**：只读（绝不 Write/Edit 代码）

## 流程

### Step 0: 读取工作流状态

1. Read `.codebuddy/workflow/state.yaml`
2. 验证 `phase.current == "phase-2-probe"` 且 `status == "pending"`（或 `in_progress` 时进入恢复模式）
3. 设置 `phase.status = "in_progress"`，`phase.started_at = 当前时间`
4. 更新 `session.last_activity = 当前时间`

### Step 1: 提取关键词

从输入中提取探路关键词：BUG → 异常方法名/页面路由/报错信息/表名；需求 → 功能关键词/目标模块/页面。

### Step 2: 并行子代理探路

一次性发送 4 个 Agent 调用（`run_in_background: true`）。子代理的 `max_turns` 设置建议：

| 子代理 | model | max_turns | 说明 |
|--------|-------|-----------|------|
| Agent A | `reasoning` | 20 | codegraph 调用链追踪 |
| Agent B | `reasoning` | 25 | DB 查询 + VO/DTO 定位 + 数据采样 |
| Agent C | `reasoning` | 20 | 影响范围 + 业务规则 |
| Agent D | `lite` | 10 | 文档查阅 |

> ⚠️ 注意：max_turns 包含 Agent 的思考和每次 MCP 工具调用的轮次（codegraph 的 `context`/`trace`/`impact` 各消耗 1-2 轮），不足会导致探路不完整。如超限，回退方案为手动探路（直接使用 Read/Grep/mysql-archive/codegraph 逐个执行）。

**Agent A: 代码结构探路** — codegraph 建立 L0-L3 调用链
1. `codegraph_context(task, maxNodes=20, includeCode=true)` — 获取入口点 + 关联符号 + 关键代码
2. `codegraph_node(symbol, includeCode=true)` — 补充调用链细节 [可选]
3. `codegraph_trace(from=Controller, to=Mapper)` — 验证完整路径 [可选]

**Agent B: 数据库结构 + 数据采样 + VO/DTO 数据视图 + 流转追踪** — 三层探路

**B1: 表结构查询**
1. `list_tables` — 确认表名
2. `describe_table(table="{目标表名}")` — 获取字段/类型/备注/关联

**B2: 真实数据采样** — 通过 API 接口获取，通过 VO/DTO 体现业务数据结构

> 数据采样优先通过 **Controller 层 API 接口**获取，确保采样数据反映的是经过业务逻辑处理后的视图，而非原始表结构。使用 `api-fetcher` MCP 工具调用本地后端服务获取实时 JSON 响应。同时保留底层 DB 采样用于值域验证。

**B2-1: API 接口数据获取**（涉及 DB 时必做，前置步骤）

通过 `codegraph` 分析代码结构定位 VO/DTO 类，再通过 `api-fetcher` 调用实际 API 获取真实响应数据。

**Step A — codegraph 定位（必须前置，确认 API 路径和 VO 结构）**
1. `codegraph_context(task="{功能描述} 的 Controller 返回数据", maxNodes=20, includeCode=true)` — 定位目标 Controller 方法，获取其返回的 VO/DTO 类名和 `@RequestMapping` 路径
2. `codegraph_node(symbol="{VO/DTO 类名}", includeCode=true)` — 获取 VO/DTO 完整的字段结构（字段名、类型、注解），记录到探路报告
3. `codegraph_trace(from="{Controller 列表方法}", to="{Mapper}")` — 验证业务数据从 DB→Entity→VO/DTO 的完整映射链路
4. 标注 **每个 VO/DTO 字段的赋值来源**（直接映射/枚举转换/字典翻译/计算派生/聚合统计）

**Step B — api-fetcher 调用（可选，后端运行时可执行）**
1. `api_login` — 获取 JWT token（通过 `.mcp.json` 中配置的登录方式；dev profile 走 `/sys/login` 免验证码，其他 profile 可加 `--use-mlogin`）
2. `api_list path="/{Controller 基路径}/{list 端点}" params={pageNo:1, pageSize:3}` — 调用列表接口，返回 JSON 数据
3. 对比 Step A 的 VO/DTO 字段结构与 API 实际返回的 field name/sample，**标注差异**（如 `@JsonIgnore` 字段未返回、null 字段输出差异、额外字段等）
4. 如存在详情接口，使用 `api_get_by_id path="/{详情端点}" id="{上一步中的记录ID}"` 获取单条完整数据

> 当后端服务未运行时，跳过 Step B，仅基于 codegraph 分析的 VO/DTO 结构进行探路（结果中标注"API 未验证"）。

**B2-2: DB 真实数据采样**（`execute_query`，仅 SELECT，涉及 DB 时必做）
1. `execute_query(query="SELECT * FROM {目标表} LIMIT 3")` — 正常流程数据行，对照 VO/DTO 字段验证字段映射关系
2. `execute_query(query="SELECT DISTINCT {枚举/状态字段} FROM {目标表}")` — 枚举/字典值域覆盖
3. `execute_query(query="SELECT * FROM {目标表} WHERE {条件} LIMIT 3")` — 边界案例数据（如状态=已删除、数值=0、日期=空），最多 2 个场景
4. 对照 B2-1 的 VO/DTO 字段结构，标注 **每个表字段在 VO 中的映射关系**（字段名转换、类型转换、值域转换）

> 约束见下方"约束"章节。

**B3: 数据流转追踪**
1. `codegraph_trace(from=Controller, to=Mapper)` — 验证写入数据的完整链路
2. `codegraph_node("{写入Service方法}")` — 查看数据转换/计算逻辑
3. 标注"谁写入→数据从哪来→经过哪些转换→VO/DTO 赋值→最终存到哪"

**Agent C: 影响范围 + 业务规则**
1. `codegraph_impact(symbol)` — 影响范围分析
2. `codegraph_explore(query, maxFiles=8)` — 批量获取源码
3. `codegraph_context(task="扫描 {模块} 业务逻辑模式")` — 识别 if/switch/枚举/权限等

**Agent D: 查阅项目文档**
1. Read `docs/INDEX.md` → 定位目录
2. Read `docs/探路报告/INDEX.md` → 复用同类报告
3. Read `docs/业务规则/{模块}` → 理解业务约束
4. 查最近修改记录

### Step 3: 合并结果 → 生成探路报告

输出到 `docs/探路报告/YYYY-MM-DD-{功能简述}.md`：

| 层级 | 内容 | 必填 |
|------|------|------|
| **澄清摘要** | Phase 1 澄清结论（从对话传递） | ✅ |
| **L0** 层状导航 | 页面→API→Service→Mapper→DB 完整调用链 | ✅ |
| **L1** 页面入口 | 路由、Vue 组件、API 调用链路 | ✅ |
| **L2** API 入口 | Controller→Service→Mapper 每步精确行号 | ✅ |
| **L3** 实现类索引 | 所有相关类文件路径和行号 | ✅ |
| **L4** 数据库 | 表/字段/值域/关联（涉及 DB 时） | 按需 |
| **L4.5** VO/DTO 数据视图 | Controller 返回的 VO/DTO 类名+字段结构+赋值来源（涉及 DB/后端时） | ✅ |
| **L5** 前端 API/组件 | API 文件 + 组件依赖清单（涉及前端时） | 按需 |
| **测试数据样例** | 正常流程数据行 + 边界值覆盖 + 字典/枚举 DISTINCT + VO/DTO 字段映射对照（涉及 DB 时） | ✅ |
| **影响范围** | codegraph_impact 分析结果 | 推荐 |

**报告不应包含**：修改建议、实施方案（Phase 3/4 的职责）。

### Step 4: 更新探路报告索引

在 `docs/探路报告/INDEX.md` 表**顶部**插入新行（<!-- ↓↓↓ 新记录插入到下面这行下方，保持日期倒序 ↓↓↓ --> 下面），格式：

```
| {日期} | [{简述}](./{文件名}) | {一行摘要} |
```

### Step 5: 更新工作流状态（Phase 出口）

1. 更新 `artifacts.probe_report.path = "docs/探路报告/{最新文件名}"`, `artifacts.probe_report.updated_at = 当前时间`
2. Phase 出口：
   - `phase.status = "completed"`, `phase.completed_at = 当前时间`
   - `progress.phases_completed.append("phase-2-probe")`
   - `phase.current = "phase-2.5-quality-gate"`, `phase.status = "pending"`
   - `metrics_snapshot.phase_durations[phase-2-probe] = 耗时分钟数`
   - `metrics_snapshot.total_duration_min = 累加值`
3. 更新 `session.last_activity = 当前时间`

### ⚡ 探路前必查：全路径 + 封装层检测

首次探路时必须检查以下两项，避免遗漏：

**1. 全路径覆盖** — 同一功能可能通过多个 UI 入口触发，每个入口的代码路径不同：
- 列表页内联编辑
- 详情弹框（双击行）
- 新增/编辑弹框（按钮点击）
- 自定义弹框内的表单
- 每个路径的 `formSchema`/`componentProps` 可能独立定义

**2. 封装层/中间件检测** — 数据流中可能存在拦截或包装层：
- `onChange` 是否有 wrapper（如 FormModal2 的 setFunction）
- `componentProps` 是否有包裹函数（在渲染时二次封装原始 props）
- `formActionType` 作用域是否指向正确的表单实例
- `treeParams` 是否为快照值而非响应式绑定

## 完整性自检

- [ ] L0-L3 每步有文件:行号，调用链贯通
- [ ] 使用了并行子代理（至少 codegraph + mysql-archive）
- [ ] 已查阅项目文档
- [ ] 影响范围评估已包含
- [ ] 不确定处标注"(待验证)"
- [ ] 子代理超限时已执行手动探路回退
- [ ] 不包含修改建议
- [ ] 涉及 DB 时已采样真实数据行（正常 + 边界）
- [ ] 涉及 DB 时已标注数据写入链路（谁写入→从哪来→经过哪些转换→最终存到哪）
- [ ] 涉及后端时已记录 VO/DTO 类名和字段结构（反映业务数据视图）
- [ ] VO/DTO 字段赋值来源已标注（直接映射/枚举转换/字典翻译/计算派生/聚合统计）
- [ ] 表字段与 VO 字段映射关系已记录

## 超限回退（子代理超限时使用）

当子代理因 max_turns 超限失败时，按以下步骤手动探路：

1. **Agent D（文档查阅）优先**：文档类探路轻量，通常不会超限，优先获取
2. **手动执行 codegraph**：直接在当前会话中使用 `mcp__codegraph__codegraph_context` 获取调用链，而非委托子代理
3. **手动执行 mysql-archive**：直接使用 `mcp__mysql-archive__describe_table`/`execute_query` 查询表结构和数据
4. **手动 Grep 搜索**：直接搜索关键字定位代码
5. **记录超限原因**：在探路报告的质量检查章节标注 C20 异常

> 手动探路效率低于并行子代理，但能保证探路不中断。超限原因应在 Phase 6.7 复盘中沉淀为改进项。

## 约束

- 绝不写代码或修改建议。精确行号。标注"(待验证)"。
- `execute_query` 仅允许 SELECT 语句，严禁 DML（INSERT/UPDATE/DELETE）。
- 边界条件采样最多 2 个场景，避免过度查询。

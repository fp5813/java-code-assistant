---
name: archive-dev/phases/phase-5.5-review
description: "文档代码审核：FE/BE 并行子代理（lite+reasoning 不同模型）核对探路报告/规格/代码质量，主流程做 API 文档一致性比对。"
version: 2.1.0
tags: [archive, codebuddy-only, review, documentation, sub-agents]
role: codebuddy-reviewer
model: deepseek-v4-flash
tools: [Read, Write, Edit, Grep, Agent]
references:
  - ../references/codegraph-reference.md
---

# Phase 5.5: 文档代码审核（并行子代理）

> 代码改完了不等于做完了。探路报告/规格文档/API 文档可能已与最终代码不一致。
>
> 本次审核改为混合模式：FE/BE 并行子代理各自审核，主流程做 API 文档一致性比对。

## 职责

**输入**：Phase 5 代码 + 探路报告 + 规格文档 + API 接口文档  
**输出**：即时审核结果（Phase 6 生成修改记录时将审核结论纳入记录）  
**模式**：只读（发现不一致时更新文档，不修改代码）

## 流程

### Step 0: 读取工作流状态

1. Read `.codebuddy/workflow/state.yaml`，验证 `phase.current == "phase-5.5-review"`
2. 验证前置制品存在：`artifacts.code_changes` 非空
3. 设置 `phase.status = "in_progress"`, `phase.started_at = 当前时间`

### Step 1: 并行子代理审核

一次性发送 2 个 Agent 调用（`run_in_background: true`），无依赖关系，并行执行。

**Agent FE (model: `lite`)** — 快速前端审核，专注语法/组件/枚举/参数一致性

| 审核项 | 方法 | 通过条件 | 文件范围 |
|--------|------|---------|---------|
| 前端文件存在性 | `ls` 确认 | 探路报告引用的 `.vue`/`.ts` 文件存在 | `archive-web/src/` |
| 前端方法/组件名 | Grep 搜索 | 组件名、API 方法名与代码一致 | 前端文件 |
| 前端行号准确 | Read 确认 | 与实际相差 ≤3 行 | 前端文件 |
| 前端 AC 完成度 | Grep 搜索实现 | AC 有对应逻辑 | 前端文件 |
| 前端 Scope 边界 | Read 确认修改范围 | 未超出声明范围 | 前端文件 |
| API 参数一致性 | 对比 API 文件与后端接口 | 参数名/类型一致 | 前端 API 文件 |
| 枚举/字典引用 | Grep 检查字典 code | 枚举值在枚举定义中存在 | 前端枚举文件 |
| 组件调用链 | Read 确认组件引用 | 组件引用关系一致 | Vue 组件 |

不一致 → 更新探路报告中的路径/行号。AC 未完全实现 → 标注"部分实现"。Scope 超出 → 追加 Extra。

代码质量问题 ≥1 个 或 不一致 ≥2 个时，在输出表格"说明"列标注 **⚠️ 需复盘**。

**Agent BE (model: `reasoning`)** — 深度后端审核，专注调用链/事务/权限/异常

| 审核项 | 方法 | 通过条件 | 文件范围 |
|--------|------|---------|---------|
| 后端文件存在性 | `ls` 确认 | 引用的 `.java` 文件存在 | `jeecg-module-archivesys/` |
| 后端方法名 | Grep 搜索 | 方法签名与代码一致 | Java 文件 |
| 后端行号准确 | Read 确认 | ≤3 行偏差 | Java 文件 |
| 调用链有效 | Read 确认 | Controller→Service→Mapper 贯通 | Java 文件 |
| 后端 AC 完成度 | Grep 搜索实现 | AC 有对应逻辑 | Java 文件 |
| 后端 Scope 边界 | Read 确认修改范围 | 未超出声明范围 | Java 文件 |
| 事务边界 | Grep `@Transactional` | 多表操作有事务注解 | Service 类 |
| 权限注解 | Grep `@RequiresPermissions` | 新接口有权限控制 | Controller 类 |
| 异常处理 | Read 确认 | 异常捕获/抛出不遗漏 | Service/Controller |
| 安全审计 | Grep `@RequiresPermissions` + Read SQL 拼接 | 新接口有权限控制；SQL 使用参数化查询而非拼接 | Controller/Mapper |
| 性能风险 | Read 确认循环/批量操作 | 无 N+1 查询；批量操作使用 batch API | Service/Mapper |
| 可观察性 | Grep `@AutoLog` + Read 异常日志 | 新增接口有操作日志；异常有完整上下文信息 | Controller |

不一致 → 更新探路报告。AC 未完全实现 → 标注"部分实现"。代码质量问题→标注"⚠️ 建议"但不修改。

代码质量问题 ≥1 个 或 不一致 ≥2 个时，在输出表格"说明"列标注 **⚠️ 需复盘**。

### Step 2: 主流程执行 API 接口文档核对

等待两个子代理完成后，主流程执行全量 API 接口文档比对：

| 核对项 | 方法 | 通过条件 |
|--------|------|---------|
| 接口路径 | 对比 `@RequestMapping` 与文档 | 完全一致 |
| 请求方法 | 对比 `@GetMapping`/`@PostMapping` 等 | 一致 |
| 请求参数 | 对比 `@RequestParam`/`@RequestBody` | 参数名类型一致 |
| 响应格式 | 对比 `Result<T>` 返回 | 字段结构一致 |
| 权限注解 | 对比 `@RequiresPermissions` | 表达式一致 |

不一致 → 更新接口文档。缺失 → 按模板新增。同时更新 `docs/接口文档/00-全API索引.md`。
涉及新增模块时，同步更新 `docs/接口文档/INDEX.md` 在表顶部插入新行。

### Step 2.5: 全量文档完整性扫描（新增）

在 API 文档核对完成后，必须执行文档完整性扫描，确保代码中存在但文档缺失的部分被补全：

| 扫描项 | 方法 | 通过条件 |
|--------|------|---------|
| 接口文档覆盖度 | Grep 搜索前端 API 调用路径 (`defHttp.post/get` 中的 url)，与 `docs/接口文档/` 中的接口逐条比对 | 所有前端调用的 API 接口在文档中有记录 |
| 业务规则文档覆盖度 | 检查修改涉及的业务逻辑是否在 `docs/业务规则/` 中有对应文档 | 涉及的业务规则已记录 |
| 全API索引参数准确性 | 比对 `00-全API索引.md` 中的参数描述与 Controller 代码实际参数 | 参数名/说明与实际一致 |
| Controller 覆盖度 | 检查修改涉及的 Controller 是否在接口文档中有对应章节 | Controller 在文档中有记录 |

**扫描流程**：
1. 搜索 `archive-web/src/views/archive/` 中与本次修改相关的前端文件，提取所有 `defHttp.post/get` 的 url 路径
2. 搜索后端 Controller 的 `@RequestMapping` 基路径
3. 在 `docs/接口文档/` 中搜索每个 API 路径是否存在
4. 缺失的 API 文档 → 在当前 Phase 5.5 内按模板补全
5. 参数描述不准确 → 修正 `00-全API索引.md`
6. 更新 `docs/接口文档/INDEX.md` 的日期行

**输出**：扫描结果追加到 Step 4 的标准表格，新增一行 `文档完整性扫描`。

### Step 3: 端到端场景验证（新增）

审核通过后，必须按 Phase 3 规格文档的 AC 逐条执行端到端场景验证，模拟用户真实操作路径：

| 验证项 | 方法 | 验证方式 | 通过条件 |
|--------|------|---------|---------|
| 编译验证 | `mvn compile -pl 对应模块 -am -q` | 后端编译 | exit code 0 |
| API 响应验证 | `curl` 或 `api-fetcher` | 调用实际接口 | 返回预期数据 |
| 数据库验证 | `mysql-archive.execute_query` | 检查数据正确性 | 与预期一致 |
| 竞态分析 | 检查 async 函数中 API 返回后值是否已变 | 代码审查 | 无竞态风险 |
| AC 逐条验证 | 按规格文档 AC 清单逐条核对代码实现 | 代码+数据审查 | 全部覆盖 |
| 边界场景 | 空数据 / 错误参数 / 重复操作 | 代码+数据审查 | 有正确处理 |

**验证流程**：
1. 读取规格文档 AC 清单
2. 对每条 AC，确认代码中有对应的实现逻辑
3. 后端修改验证 `mvn compile` 编译通过
4. 数据库修改验证 `mysql-archive` 查询确认
5. 无法通过 API 验证的交互步骤标注 "⚠️ 需人工测试"
6. 验证发现与 AC 不符 → 标记回退 Phase 5，在输出表格追加 "🚫 回退 Phase 5"

**影响**: 验证结果追加到 Step 4 的标准表格底部。

### Step 4: 合并结果为标准表格

汇总所有审核结果，输出 7 行标准表格：

```markdown
## 文档代码审核（Phase 5.5）
| 审核项 | 状态 | 说明 |
| FE-探路报告 | ✅ 一致 / 🔧 已更新 | {说明} |
| FE-规格文档 | ✅ 一致 / 🔧 已更新 | {说明} |
| FE-代码质量 | ✅ 通过 / ⚠️ 建议 | {说明} |
| BE-探路报告 | ✅ 一致 / 🔧 已更新 | {说明} |
| BE-规格文档 | ✅ 一致 / 🔧 已更新 | {说明} |
| BE-代码质量 | ✅ 通过 / ⚠️ 建议 | {说明} |
| API接口文档 | ✅ 一致 / 🔧 已更新 / 🆕 已新增 | {说明} |
| **文档完整性扫描** | ✅ 全覆盖 / ✅ 全覆盖（X 项已补全） / ❌ 有遗漏未补 | {缺失项数 / 补全项数} |
| **端到端验证** | ✅ 通过 / 🚫 回退 Phase 5 / ⚠️ 需人工测试 | {说明} |
```

### 复盘标记

审核结果中自动标记是否需要进入 Phase 6.7 复盘：

| 标记条件 | 标记为 |
|---------|--------|
| FE-代码质量 / BE-代码质量 为 ⚠️ 建议 | 🔄 需复盘 |
| 探路报告/规格文档/API文档 ≥2 项不一致 | 🔄 需复盘 |
| 全部通过（无 ⚠️ 无不一致） | ➡️ 无需复盘 |

标记规则由主流程在执行 Step 3 合并且输出表格时一并判定，将结果传递给 Phase 6 Step 6。

## 完整性自检

- [ ] 两个子代理都已返回结果
- [ ] FE-探路报告/规格文档/代码质量三项均已审核
- [ ] BE-探路报告/规格文档/代码质量三项均已审核
- [ ] API 接口文档全量比对完成
- [ ] docs/接口文档/INDEX.md 已同步（涉及新增模块时）
- [ ] docs/接口文档/00-全API索引.md 已同步
- [ ] **文档完整性扫描已完成**：前端 API 调用路径与接口文档逐条比对，缺失文档已补全
- [ ] 代码问题已退回 Phase 5（如有）
- [ ] 标注了审核日期

## 约束

- 只改文档不改代码（代码问题回 Phase 5）
- Agent FE 只审前端文件路径（`archive-web/src/`）
- Agent BE 只审后端文件路径（`jeecg-module-archivesys/`）
- 标注更新日期
- 审核记录必输出
- API 文档缺失必须补充
- 涉及新增模块时同步更新接口文档目录

### Phase 出口

1. 更新 `artifacts.review.status`, `artifacts.review.issues`, `artifacts.review.summary`
2. 更新 `artifacts.gate_3.status`, `artifacts.gate_3.failed_items`
3. Phase 出口：
   - `phase.status = "completed"`, `phase.completed_at = 当前时间`
   - `progress.phases_completed.append("phase-5.5-review")`
   - 通过时：`phase.current = "phase-6-record"`, `phase.status = "pending"`
   - 有代码问题时：`phase.current = "phase-5-code"`, `phase.status = "pending"`, `progress.phases_blocked.append("phase-5-code: 审核发现代码问题")`, `metrics_snapshot.gate_retries += 1`
   - `metrics_snapshot.phase_durations[phase-5.5-review] = 耗时分钟数`
4. `gates_summary` 更新计数
5. 更新 `session.last_activity = 当前时间`

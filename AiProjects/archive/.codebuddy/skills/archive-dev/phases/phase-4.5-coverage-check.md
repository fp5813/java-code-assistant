---
name: archive-dev/phases/phase-4.5-coverage-check
description: "实施计划覆盖验证：检查规格与计划的 AC 覆盖率、文件覆盖率、术语一致性，确保零遗漏后进入 Phase 5。"
version: 1.2.0
tags: [archive, codebuddy-only, coverage-check, read-only]
role: codebuddy-analyzer
model: deepseek-v4-flash
tools: [Read, Grep, Agent]
references:
  - ../references/spec-driven-development.md
---

# Phase 4.5: 覆盖验证

> 跨制品一致性分析 — 在写代码前确保规格、计划、任务三者一致。

## 职责

**输入**：规格文档（`docs/规格文档/`）+ 实施计划（`docs/实施计划/`）  
**输出**：覆盖验证报告（追加到实施计划末尾）  
**模式**：只读（不修改代码）

### Step 0: 读取工作流状态

1. Read `.codebuddy/workflow/state.yaml`，验证 `phase.current == "phase-4.5-coverage-check"`
2. 验证前置制品存在：`artifacts.plan.path` 对应的文件存在
3. 设置 `phase.status = "in_progress"`, `phase.started_at = 当前时间`

## 检查维度

### 1. AC 覆盖率 | 核心：每个 AC 至少被一个任务覆盖

| AC | 验收标准 | 覆盖任务 | 状态 |
|----|----------|----------|------|
| AC01 | {描述} | T001, T003 | ✅ |

缺少覆盖 → ❌ 回 Phase 4 补充任务。

### 2. 文件覆盖率 | 核心：规格中"直接修改文件"在任务清单中有对应

### 3. 孤儿任务检测 | 核心：无 AC/文件映射的任务需说明原因

优化类任务可保留但标注原因，无理由孤儿 ❌ 回 Phase 4 删除或补充 AC。

### 4. 术语一致性 | 核心：规格与计划的术语无漂移

### 5. 不在范围检查 | 核心：计划任务未侵入规格声明"不在范围内"的内容

### 6. 依赖关系验证 | 核心：无循环依赖，后端先行，并行任务不操作同一文件

### 7. 数据来源追溯 | 核心：规格文档中的数据来源与探路报告一致（涉及 DB 时）

| 追溯项 | 探路报告 | 规格文档 | 状态 |
|--------|---------|---------|------|
| VO/DTO 类名和字段结构 | L4.5 章节 | API 接口数据章节 | ✅/❌ |
| 表名和字段 | L4 章节 | 表结构章节 | ✅/❌ |
| 表↔VO 字段映射 | 测试数据样例章节 | 表↔VO 字段映射章节 | ✅/❌ |
| 测试数据 SQL | 测试数据样例章节 | 测试数据章节 | ✅/❌ |

缺少追溯 → ❌ 回 Phase 4 修正规格文档。

## 门控判定

| 条件 | 状态 |
|------|------|
| AC 覆盖率 = 100% AND 文件覆盖率 = 100% AND 无不在范围违规 AND 术语一致 AND 数据追溯完整 | ✅ 进入 Phase 5 |
| 以上任一项不满足 | ❌ 回 Phase 4 |

> 孤儿任务有合理理由（prefactor 等技术任务）→ ✅ 标注原因后通过，不视为失败项。

## 输出

```markdown
## 覆盖验证（Phase 4.5 Gate）
**AC 覆盖率**: {covered}/{total} = {percent}%
**文件覆盖率**: {covered}/{total} = {percent}%
**术语一致性**: {consistent}/{total} = {percent}%
**数据来源追溯**: {consistent}/{total} = {percent}%（涉及 DB 时）
**不在范围违规**: {count} 项
**门控状态**: {✅ 通过 / ❌ 不通过}
**处理建议**: {根据门控状态的下一步}
```

## 约束

- 绝不写代码。发现不一致只标注不自动修改。用具体指标说话，不过度分析（最多 20 条发现）。

### Phase 出口

1. 更新 `artifacts.gate_4_5.status`, `artifacts.gate_4_5.score`, `artifacts.gate_4_5.failed_items`
2. Phase 出口：
   - `phase.status = "completed"`, `phase.completed_at = 当前时间`
   - `progress.phases_completed.append("phase-4.5-coverage-check")`
   - 通过时：`phase.current = "phase-5-code"`, `phase.status = "pending"`
   - 不通过时：`phase.current = "phase-4-plan"`, `phase.status = "pending"`, `progress.phases_blocked.append("phase-4-plan: 覆盖验证未通过")`, `metrics_snapshot.gate_retries += 1`
   - `metrics_snapshot.phase_durations[phase-4.5-coverage-check] = 耗时分钟数`
3. `gates_summary` 更新计数
4. 更新 `session.last_activity = 当前时间`

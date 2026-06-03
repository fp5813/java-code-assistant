---
name: fullstack-flow/phases/phase-5-code
description: "最小修改：按实施计划执行，FE/BE 并行子代理，过影响范围自检，在 IDE 中编译验证。"
version: 2.1.0
tags: [fullstack, codebuddy-only, code, implementation]
role: codebuddy-coder
model: deepseek-v4-pro
tools: [Read, Write, Edit, Grep, Agent]
references:
  - ../references/spec-driven-development.md
---

# Phase 5: 最小修改

> 按计划执行，最小影响原则。

## 职责

**输入**：实施计划（`docs/实施计划/`）  
**输出**：修改后的代码文件  
**模式**：编码

## 流程

### 5.0 读取工作流状态

1. Read `.codebuddy/workflow/state.yaml`，验证 `phase.current == "phase-5-code"`
2. 验证前置制品存在：`artifacts.plan.path` 对应的文件存在，`artifacts.gate_4_5.status == "passed"`
3. 设置 `phase.status = "in_progress"`, `phase.started_at = 当前时间`

### 5.1 影响范围自检（修改前逐项确认）

| 检查项 | 问题 |
|--------|------|
| 前后端同步 | 改 Vue 时 Entity 要加字段？ |
| Service 接口 | Impl 改了，Interface 要改？ |
| 权限注解 | 新接口需要 @RequiresPermissions？ |
| 事务边界 | 多表操作需要 @Transactional？ |
| 文档同步 | 修改是否影响已有文档/业务规则？ |

### 5.1.5 ⚡ 异步竞态分析（修改 async 函数前必查）

修改包含 `await` 的异步函数时，分析以下竞态场景并标注处理方式：

| 场景 | 问题 | 处理方式 |
|------|------|---------|
| API 响应时值已变 | `await` 期间 props/state 被外部修改，旧响应覆盖新状态 | API 返回后检查 `originalValue !== currentValue` |
| 并发请求 | 多次调用在 await 期间堆积，后返回的旧结果覆盖 | 放弃返回前检查值是否仍匹配 |
| 守卫失效 | `initializing` 等普通变量守卫在响应式系统外 | 改用 ref 或捕获 originalValue 比较 |

### 5.2 任务分配

根据计划将任务拆为前端（Vue/API/data.ts/枚举）和后端（Controller/Service/Mapper/Entity）两组。

### 5.3 并行执行

有依赖 → 串行（如 FE 依赖 BE，则 BE 先完成）。无依赖 → 并行（默认情况）。

**Agent FE**: 前端修改 — 按计划逐一执行，每完成一个任务打勾。验证：在 IDE 中编译验证

**Agent BE**: 后端修改 — 按计划逐一执行，每完成一个任务打勾。验证：在 IDE 中编译验证

> **上下文提示**：子代理返回主会话时，输出格式限定为"任务完成清单 + 关键决策点"的摘要形式（每代理 ≤1K tokens），避免完整对话日志填充主会话上下文。

### 5.4 合并验证

| 验证项 | 方法 | 通过条件 |
|--------|------|---------|
| 编译通过 | 在 IDE 中编译验证（后端 IDE 编译 / 前端 TypeScript 检查） | 无编译错误 |
| 测试通过 | `npx jest` / `mvn test` | 全部通过或已知失败 |
| 配置引用 | Grep 检查引用的接口路径/方法名一致 | 前后匹配 |

## 自检

- [ ] 所有计划任务已执行并打勾
- [ ] FE/BE 子代理都已返回
- [ ] IDE 编译验证通过
- [ ] 影响范围自检清单已确认
- [ ] 修改范围未超出规格 Scope

## 约束

- 严格按计划执行，不自行增删任务。最小修改（不改无关代码）。不改规格。不引入新依赖（除非计划中明确）。
- **Phase 5.5 退回处理**：如审核发现代码问题退回 Phase 5，优先修复退回项，依次处理。如修复涉及计划外任务，在修改记录中标注"Phase 5.5 退回修复"并简要说明原因，无需更新实施计划。最小修改原则在退回修复时略微放宽，允许修复与退回项直接相关的紧邻代码异味。

### Phase 出口

1. 更新 `artifacts.code_changes` 列表（记录每个修改文件的 path/type/summary）
2. Phase 出口：
   - `phase.status = "completed"`, `phase.completed_at = 当前时间`
   - `progress.phases_completed.append("phase-5-code")`
   - `phase.current = "phase-5.5-review"`, `phase.status = "pending"`
   - `metrics_snapshot.phase_durations[phase-5-code] = 耗时分钟数`
3. 更新 `session.last_activity = 当前时间`

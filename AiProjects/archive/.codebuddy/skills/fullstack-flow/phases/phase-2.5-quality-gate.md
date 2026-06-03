---
name: fullstack-flow/phases/phase-2.5-quality-gate
description: "探路报告质量门控：35 项检查覆盖 L0-L5 完整性、文档查阅、工具规范性。通过率 100% 方可进入 Phase 3。"
version: 2.1.0
tags: [fullstack, codebuddy-only, quality-gate, read-only]
role: codebuddy-gatekeeper
model: deepseek-v4-flash
tools: [Read, Grep]
---

# Phase 2.5:  质量门控

> Checklist = "需求编写的单元测试" — 检验探路报告本身的质量。

## 职责

**输入**：探路报告（`docs/探路报告/YYYY-MM-DD-{简述}.md`）  
**输出**：检查结果（追加到探路报告末尾的 `## 质量检查` 章节）  
**模式**：只读（不修改源文件）

## 流程

### Step 0: 读取工作流状态

1. Read `.codebuddy/workflow/state.yaml`，验证 `phase.current == "phase-2.5-quality-gate"`
2. 设置 `phase.status = "in_progress"`, `phase.started_at = 当前时间`

## 检查项（35 项）

### L0 层状导航完整性
| # | 检查项 |
|---|--------|
| C01 | 页面→API 调用链完整 |
| C02 | API→Service→Mapper 链路贯通 |
| C03 | 每步标注 file:line |
| C04 | 涉及 DB 时包含数据库节点 |

### L1 页面入口覆盖度
| # | 检查项 |
|---|--------|
| C05 | 路由路径明确 |
| C06 | Vue 组件精确到文件:行号 |
| C07 | API 调用链路完整（组件→API→HTTP 端点） |
| C08 |  列出相关子组件及路径 |

### L2 API 入口精确性
| # | 检查项 |
|---|--------|
| C09 | Controller 方法有 file:line |
| C10 | Service 方法有 file:line |
| C11 | Mapper/XML 行号已标注 |
| C12 | Controller 基路径已标注 |

### L3 实现类索引完整性
| # | 检查项 |
|---|--------|
| C13 | Controller 类列出 |
| C14 | Service 接口+实现列出 |
| C15 | Mapper/XML 列出 |
| C16 | Entity/DTO 列出 |

### 探路工具使用
| # | 检查项 |
|---|--------|
| C17 | 使用至少两种探路工具（codegraph + mysql-archive） |
| C18 | 查阅了项目文档 |
| C19 | 业务规则发现已记录 |
| C20 | 探路深度合理（每代理 ≤3 次调用） |
| C21 |  不确定处标注"(待验证)" |

### L4 数据库覆盖度
| # | 检查项 |
|---|--------|
| C22 | 涉及 DB 时列出表名 + Entity 映射 |
| C23 | 关键字段有类型和值域 |

### L5 前端 API 与组件
| # | 检查项 |
|---|--------|
| C24 | 涉及前端时列出 API 文件 + 方法 |
| C25 | 涉及前端时列出子组件 |

### 报告避免项
| # | 检查项 |
|---|--------|
| C26 | 不含修改建议 |
| C27 | 不含实施方案 |

### ⚡ 复盘新增：探路覆盖率检查（从 C28 开始）
| # | 检查项 |
|---|--------|
| C28 | 是否覆盖了所有 UI 入口路径（列表页/详情弹框/编辑弹框/自定义弹框） |
| C29 | 是否检查了封装层/中间件（onChange wrapper、componentProps 包裹、formActionType 作用域） |
| C30 | 异步函数是否标注了竞态风险（await 期间 state 被外部修改的可能性） |

### 📊 数据覆盖检查（C31-C35）
| # | 检查项 |
|---|--------|
| C31 | 涉及 DB 时已查询真实数据行（至少正常流程 3 行 + 边界场景 DISTINCT）；`LIMIT 3` 返回行覆盖了不同状态值分布（同一状态多行时标注其代表性） |
| C32 | 枚举/字典/状态字段值域已列出（通过 DISTINCT 查询或 `@Dict` 注解解析） |
| C33 | 数据写入链路已标注（哪个 Controller→Service→Mapper 负责数据的增/删/改）**且**数据转换逻辑已说明（列举字段 A→转换规则→目标字段的映射关系） |
| C34 | 涉及后端时已记录 VO/DTO 类名和字段结构（反映业务数据视图而非原始表结构）；VO/DTO 字段赋值来源已标注（直接映射/枚举转换/字典翻译/计算派生/聚合统计） |
| C35 | 表字段与 VO/DTO 字段映射关系已对照记录（列出不一致或转换之处，如字段名不同、类型转换、值域映射） |

### 自适应跳检

| Ticket 类型 | 跳过项 |
|-------------|--------|
| 纯前端 | C04, C11, C15, C16, C22, C23, C31, C32, C33, C34, C35 |
| 纯后端 | C05-C08, C24, C25 |
| 数据库修改 | C05-C08, C09-C12 部分, C24, C25, C33（如果只读查询），C34, C35（如果无 VO/DTO 变更） |
| 纯配置 | 仅保留 C17-C21 |

### 出口更新：更新工作流状态

更新 `artifacts.gate_2_5` 的 `status/score/failed_items/summary` 字段。

## 门控判定

通过率 100% → ✅ 进入 Phase 3  
＜100% → ❌ 回到 Phase 2，重新探路

## 输出

```markdown
## 质量检查（Phase 2.5 Gate）
**通过率**: {pass}/{total} = {percent}%
**失败项**:  | # | 检查项 | 原因 | 修复建议 |
**门控状态**: {✅ 通过 / ⚠️ 警告 / ❌ 不通过}
**复盘标记**: {失败项 ≥1 时标注 ⚠️ 需复盘，供 Phase 6.7 入口判定}
```

不通过时停止流程，输出失败项清单。

失败项 ≥1 时，标记 **⚠️ 需复盘**，供 Phase 6 Step 6 复盘四问读取。

## 约束

- 绝不写代码。只追加不覆盖。透明公开（每个失败项说明具体原因）。

### Phase 出口

1. 更新 artifacts.gate_2_5：status/score/failed_items/summary
2. Phase 出口：
   - `phase.status = "completed"`, `phase.completed_at = 当前时间`
   - `progress.phases_completed.append("phase-2.5-quality-gate")`
   - 通过时：`phase.current = "phase-3-spec"`, `phase.status = "pending"`
   - 不通过（<100%）时：`phase.current = "phase-2-probe"`, `phase.status = "pending"`, `progress.phases_blocked.append("phase-2-probe: 门控未通过")`, `metrics_snapshot.gate_retries += 1`
   - `metrics_snapshot.phase_durations[phase-2.5-quality-gate] = 耗时分钟数`
3. 更新 `session.last_activity = 当前时间`
4. `gates_summary` 更新计数

---
name: archive-dev/phases/phase-6.6-audit
description: "业务规则审计：codegraph 扫描全项目业务逻辑模式（状态判断/枚举/权限/数据过滤），识别未归档规则并输出审计报告。"
version: 1.1.0
tags: [archive, codebuddy-only, audit, business-rule, read-only]
role: codebuddy-auditor
model: deepseek-v4-flash
tools: [Read, Grep, Agent]
references:
  - ../references/codegraph-reference.md
---

# Phase 6.6: 业务规则审计

> Phase 6.5 只记录被修改触及的规则。稳定代码中的业务规则无人发现。本阶段主动扫描补齐缺口。

## 职责

**输入**：无（独立运行，不依赖 BUG/需求）  
**输出**：`docs/业务规则/审计/YYYY-MM-DD-审计报告.md`  
**模式**：只读（不修改源代码）

## 流程

### Step 1: 确定审计范围

- 全量审计（初次推荐）→ 扫描全部模块
- 模块审计 → 指定模块
- 差异审计 → 扫描已覆盖模块之外的缺口

### Step 2: codegraph 扫描 6 类业务规则模式

| # | 模式 | codegraph 查询 |
|---|------|---------------|
| 1 | 状态/字典值判断 | `codegraph_context(task="找出 equals('archiveStatus') 或字典值比较的 if/switch")` |
| 2 | 枚举类 | `codegraph_search(query="Enum", kind="class")` + `codegraph_node` |
| 3 | 权限注解 | `codegraph_context(task="找出 @RequiresPermissions")` |
| 4 | 数据权限过滤 | `codegraph_context(task="找出 inSql/joinSql/archive_data_permission")` |
| 5 | 前端条件渲染 | `codegraph_context(task="找出 v-if archiveStatus")` |
| 6 | @Dict 注解 | `codegraph_context(task="@Dict 在实体类中的使用")` |

### Step 3: 对比已有规则

与 `docs/业务规则/` 对比：状态值是否已覆盖、权限标识是否已记录、枚举类是否已归档、字典编码是否已说明。

### Step 4: 输出审计报告

```markdown
# 业务规则审计报告
## 基本信息（日期/范围/已归档 N 条/未归档 N 条）
## 未归档规则清单
| # | 位置 | 模式 | 描述 | 操作建议 |
|---|------|------|------|---------|
| 1 | `file:line` | 状态判断 | `if("X".equals(status))` | 新增规则文件 |
## 按模块汇总
| 模块 | 已有规则 | 未归档数 | 优先级 |
```
## 自检

- [ ] 已确定审计范围
- [ ] 已完成 ≥3 类业务模式扫描
- [ ] 已与已有规则对比
- [ ] 报告已输出

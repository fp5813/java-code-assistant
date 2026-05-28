---
name: archive-dev/phases/phase-5.5-review
description: "文档代码审核：核对探路报告/规格/API 接口文档与最终代码一致性，发现不一致时更新文档。"
version: 1.1.0
tags: [archive, codebuddy-only, review, documentation]
role: codebuddy-reviewer
model: deepseek-v4-flash
tools: [Read, Grep, Agent]
references:
  - ../references/codegraph-reference.md
---

# Phase 5.5: 文档代码审核

> 代码改完了不等于做完了。探路报告/规格文档/API 文档可能已与最终代码不一致。

## 职责

**输入**：Phase 5 代码 + 探路报告 + 规格文档 + API 接口文档  
**输出**：审核记录（追加到 Phase 6 修改记录末尾），不一致时更新 `docs/接口文档/`  
**模式**：只读（发现不一致时更新文档，不修改代码）

## 核对项

### 探路报告核对

| 核对项 | 方法 | 通过条件 |
|--------|------|---------|
| 文件存在性 | ls 确认 | 所有引用文件存在 |
| 方法名匹配 | Grep 搜索 | 签名与代码一致 |
| 行号准确 | Read 确认 | 与实际相差 ≤3 行 |
| 调用链有效 | Read 确认 | 调用关系一致 |

不一致 → 更新探路报告中的路径/行号。

### 规格文档核对

| 核对项 | 方法 | 通过条件 |
|--------|------|---------|
| AC 完成度 | Grep 搜索实现 | 所有 AC 有对应逻辑 |
| Scope 边界 | Read 确认修改范围 | 未超出声明范围 |

AC 未完全实现 → 更新状态为"部分实现"。Scope 超出 → 追加 Extra 章节。

### API 接口文档核对

| 核对项 | 方法 | 通过条件 |
|--------|------|---------|
| 接口路径 | 对比 `@RequestMapping` 与文档 | 完全一致 |
| 请求方法 | 对比 `@GetMapping` 等 | 一致 |
| 请求参数 | 对比 `@RequestParam`/`@RequestBody` | 参数名类型一致 |
| 响应格式 | 对比 `Result<T>` 返回 | 字段结构一致 |
| 权限注解 | 对比 `@RequiresPermissions` | 表达式一致 |

不一致 → 更新接口文档。缺失 → 按模板新增。同时更新 `docs/知识库/topics/00-全API索引.md`。

## 输出

```markdown
## 文档代码审核（Phase 5.5）
| 审核项 | 状态 | 说明 |
| 探路报告 | ✅ 一致 / 🔧 已更新 | {说明} |
| 规格文档 | ✅ 一致 / 🔧 已更新 | {说明} |
| API接口文档 | ✅ 一致 / 🔧 已更新 / 🆕 已新增 | {说明} |
```

## 约束

- 只改文档不改代码（代码问题回 Phase 5）。标注更新日期。审核记录必输出。API 文档缺失必须补充。

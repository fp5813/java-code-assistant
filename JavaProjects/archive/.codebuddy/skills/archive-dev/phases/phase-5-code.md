---
name: archive-dev/phases/phase-5-code
description: "最小修改：按实施计划执行，FE/BE 并行子代理，过影响范围自检，编译/测试验证。"
version: 2.1.0
tags: [archive, codebuddy-only, code, implementation]
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

### 5.1 影响范围自检（修改前逐项确认）

| 检查项 | 问题 |
|--------|------|
| 前后端同步 | 改 Vue 时 Entity 要加字段？ |
| Service 接口 | Impl 改了，Interface 要改？ |
| 权限注解 | 新接口需要 @RequiresPermissions？ |
| 事务边界 | 多表操作需要 @Transactional？ |
| 文档同步 | 修改是否影响已有文档/业务规则？ |

### 5.2 任务分配

根据计划将任务拆为前端（Vue/API/data.ts/枚举）和后端（Controller/Service/Mapper/Entity）两组。

### 5.3 并行执行

有依赖 → 串行（如 FE 依赖 BE，则 BE 先完成）。无依赖 → 并行（默认情况）。

**Agent FE**: 前端修改 — 按计划逐一执行，每完成一个任务打勾。验证：`npx jest {模块}` / `npx vue-tsc --noEmit`

**Agent BE**: 后端修改 — 按计划逐一执行，每完成一个任务打勾。验证：`mvn compile -pl jeecg-boot-module/jeecg-module-archivesys`

### 5.4 合并验证

| 验证项 | 方法 | 通过条件 |
|--------|------|---------|
| 编译通过 | `mvn compile` | 无编译错误 |
| 类型检查 | `vue-tsc --noEmit` | 无类型错误 |
| 测试通过 | `npx jest` / `mvn test` | 全部通过或已知失败 |
| 配置引用 | Grep 检查引用的接口路径/方法名一致 | 前后匹配 |

## 自检

- [ ] 所有计划任务已执行并打勾
- [ ] FE/BE 子代理都已返回
- [ ] 前端类型检查通过
- [ ] 后端编译/测试通过
- [ ] 影响范围自检清单已确认
- [ ] 修改范围未超出规格 Scope

## 约束

- 严格按计划执行，不自行增删任务。最小修改（不改无关代码）。不改规格。不引入新依赖（除非计划中明确）。

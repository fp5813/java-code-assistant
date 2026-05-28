---
name: archive-dev/phases/phase-6.5-rule-sync
description: "业务规则同步：评估本次修改是否涉及业务规则，涉及则更新 docs/业务规则/ 并同步到 .codebuddy/rules/。"
version: 3.1.0
tags: [archive, codebuddy-only, rule-sync, documentation]
role: codebuddy-recorder
model: deepseek-v4-flash
tools: [Read, Grep, Agent]
references:
  - ../references/change-record-detailed.md
---

# Phase 6.5: 业务规则同步

> 老项目需要持续沉淀业务规则，每次修改都是更新规则的机会。文档一致性核对已移至 Phase 5.5。

## 职责

**输入**：Phase 5 代码变更  
**输出**：更新/新增 `docs/业务规则/{模块}/*.md`，同步 `.codebuddy/rules/`  
**模式**：只读 + 写规则文件

## 流程

### Step 1: 评估是否涉及业务规则

**属于业务规则**：新增状态/条件判断、修改业务逻辑、新增字段约束、新增 API 权限规则、数据库约束变更。

**不属于业务规则**：修复空指针、修改文案、代码风格优化、性能优化。

不涉及 → 在修改记录注明"不涉及业务规则变更"，跳过后续。

### Step 2: 定位并更新规则文件

| 场景 | 操作 |
|------|------|
| 已有规则文件 + 新规则 | 追加章节 |
| 已有规则文件 + 旧规则改动 | 更新描述，标注"更新于 {日期}" |
| 无对应文件 + 涉及规则 | 按模板新建 |

### Step 3: 同步到 .codebuddy/rules/

```yaml
---
globs: ["{匹配路径模式}"]
description: "{规则简要描述}"
alwaysApply: false
---
```
- globs 精确到被影响的最小路径
- `alwaysApply: false`，已存在文件用更新操作

### Step 4: 在修改记录中注明

```markdown
## 业务规则同步
- [x] 本次修改涉及业务规则
- [x] 已更新：`docs/业务规则/{模块}/{文件名}.md`
- [x] 变更描述：{简要说明}
```

## 自检

- [ ] 已评估是否涉及业务规则
- [ ] 涉及时已更新/新建规则文件
- [ ] `.codebuddy/rules/` 已同步（globs + description + alwaysApply: false）
- [ ] globs 配置精确，避免过度加载

## 约束

- 只写规则文件，不修改源代码。有则可写，不强制。按模块组织，保持简洁。

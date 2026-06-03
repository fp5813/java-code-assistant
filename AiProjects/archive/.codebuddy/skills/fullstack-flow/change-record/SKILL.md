---
name: fullstack-flow/change-record
description: 生成修改记录文件（fullstack-flow 子技能），包含前后代码对比和回滚方案。格式参考 TEMPLATE.md，输出到 docs/修改记录/ 目录。
allowed-tools:
  - Read
  - Write(docs/**/*.md)
  - Edit(docs/**/*.md)
---

# 修改记录生成

根据当前代码变更生成修改记录文件。

## 执行流程

### Step 1: 收集修改信息

根据对话上下文或用户输入，列出所有修改的文件，分隔每个文件内的独立修改点。

### Step 2: 提取前后代码

每个修改点独立记录，包含完整上下文（前后各 3 行）。

### Step 3: 生成回滚方案

每个修改点对应一个回滚步骤，**不使用 git 命令**（手动替换代码）。

### Step 4: 写入文件

路径：`docs/修改记录/YYYY-MM-DD-功能简述.md`

### Step 5: 同步知识库（可选）

手动运行知识库同步脚本：
```
D:/LenovoSoftstore/Python/python.exe C:/Users/User/.knowledge-base/scripts/ingest_docs.py D:/JavaProjects/archive/docs
```

## 模板格式

详细模板和编写规范参考主技能的参考文档：

```
${CODEBUDDY_SKILL_DIR}/../references/change-record-detailed.md
```

## 质量检查

- [ ] 文件路径正确：`docs/修改记录/YYYY-MM-DD-功能简述.md`
- [ ] 每个修改点都有修改前 / 修改后代码对比
- [ ] 回滚方案不使用 git 命令
- [ ] 新增文件标注了创建，删除文件标注了恢复方案

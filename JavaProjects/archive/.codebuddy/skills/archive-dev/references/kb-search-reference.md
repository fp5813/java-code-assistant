# kb-search 语义搜索参考

> **⚠️ 已按需降级**：Agent B 不再使用 kb-search。历史记录改为主流程直接 `ls` + `Read`，表结构改为 `mysql-archive describe_table`。
>
> kb-search 仅作为**按需工具**保留（`archive_docs` collection），适用于需要语义搜索历史修改记录的特殊场景。
> 正常探路流程不再默认调用。

## Collection 说明

| 工具 | 用途 | 注册位置 |
|------|------|----------|
| `mcp__kb-search__kb_search` | 向量语义搜索 | 通过 MCP 调用 |

## Bug 修复标准流程

```bash
# 查历史修改记录，看之前为什么这样改
mcp__kb-search__kb_search query="{关键词}" collection="archive_docs" top_k=3

# 找相似代码参考
mcp__kb-search__kb_search query="{相关方法名}" collection="local_projects" top_k=3
```

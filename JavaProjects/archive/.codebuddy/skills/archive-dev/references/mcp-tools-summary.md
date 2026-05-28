# MCP 工具汇总

| 工具 | 用途 | 状态 |
|------|------|------|
| **`codegraph`** | **代码结构搜索 + 调用链 + 影响分析** — 主力探路工具 | **⭐ 主用** |
| `mysql-archive` | 数据库表结构查询 | 主用 |
| `kb-search` | 向量语义搜索（archive_docs collection） | 按需 |
| `Context7` | GitHub 官方文档 | 辅助 |

## 使用优先级

1. **codegraph_context** — 首选，一次调用获取全部上下文
2. **codegraph_node** — 深入关键符号 details + trail
3. **mysql-archive** — 直接查数据库表结构
4. **kb-search** — 按需查历史记录

## 详细参考

- [codegraph 使用指南](./codegraph-reference.md)
- [kb-search 使用指南](./kb-search-reference.md)（按需）

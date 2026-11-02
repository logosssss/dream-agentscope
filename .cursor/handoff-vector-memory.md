# Handoff：Advanced RAG Hybrid 之后继续落地

下一场：用户说「继续」或「继续往下推进」时，按指南清单和各模块 README「还没有」做下一块。不要改 `doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`。

## 下一场建议技能

无。按仓库 `.cursor/rules/continue-landing.mdc` 做一块就停。不要再开 `/grill-me`，除非用户明确要求。

## 从哪接着

分支：`feat/agentscope-platform-base`（不要用 `master`）。

远端：`origin/feat/agentscope-platform-base`。

下一空 Flyway 版本：**V37**。

## 已做（本块）

Advanced RAG Hybrid：`V36` 给 `kb_chunk.content` 加 ngram FULLTEXT；仓储 `searchSuccessByFulltext`；`HybridChunkMerger` 向量优先全文补缺；`KnowledgeSearchTool` Hybrid，向量失败降级纯全文。

## 下一块

以 README「还没有」为准，优先考虑：附件上传进知识库，或 Rerank。不要再补管理端 GET 列表。

不要做：RocketMQ、服务发现、MCP、配置热更新、连接串进 Nacos、密码登录、JWT。

## 另一台机器开场白

拉到含「Advanced RAG Hybrid」的分支后说：「继续」。先读这份 handoff。

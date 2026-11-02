# Handoff：Naive RAG 竖切之后继续落地

下一场：用户说「继续」或「继续往下推进」时，按指南清单和各模块 README「还没有」做下一块。不要改 `doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`。

## 下一场建议技能

无。按仓库 `.cursor/rules/continue-landing.mdc` 做一块就停。不要再开 `/grill-me`，除非用户明确要求。

## 从哪接着

分支：`feat/agentscope-platform-base`（不要用 `master`）。

远端：`origin/feat/agentscope-platform-base`。

下一空 Flyway 版本：**V36**（V34 `kb_*`，V35 `knowledge_search` 工具种子）。

## 已做（本块）

Naive RAG 竖切：`kb_knowledge_base` / `kb_document` / `kb_chunk`；`DOCUMENT_INDEX` 异步索引（切块 + 嵌入 + Milvus）；`knowledge_search` Agentic 工具（RuntimeContext 身份，只回 SUCCESS）；接入 `/api/kb` 建库、上传、status、reindex；正文上限 2MB；`@EnableAsync`。

## 下一块

以 README「还没有」为准，优先考虑：知识库列表/删除、额度扩到 token/费用，或 Advanced RAG（全文/Hybrid）。不要再补管理端 GET 列表。

不要做：RocketMQ、服务发现、MCP、配置热更新、连接串进 Nacos、密码登录、JWT。

## 另一台机器开场白

拉到含「Naive RAG」的分支后说：「继续」。先读这份 handoff。

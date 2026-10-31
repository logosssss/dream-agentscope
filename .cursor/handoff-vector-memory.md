# Handoff：搭子记忆 × Milvus 整合

下一场：用户说「继续往下推进」时，只做第一刀 `agent-vector`。不要做抽取、嵌入、召回、注入。不要改 `doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`。

## 下一场建议技能

无。按仓库 `.cursor/rules/continue-landing.mdc` 做一块就停。不要再开 `/grill-me`，方案已锁。

## 从哪接着

分支：`feat/agentscope-platform-base`（不要用 `master`）。

远端：`origin/feat/agentscope-platform-base`。本机 **领先 5 个提交且未推送**（早先 `github.com:443` 失败）。HEAD：`1ad5744`。

**未提交（必须先带走，否则另一台没有记忆表）：**

- `agent-state/src/main/resources/db/migration/V27__companion_memory.sql`
- `agent-state`：`EpisodicMemoryRepository` / `UserProfileRepository` + MySQL / Absent 实现
- `agent-bootstrap/src/test/java/com/zhu/bootstrap/CompanionMemoryRepositoryTest.java`
- `agent-state/README.md`、`agent-access/README.md`、`CompanionPersona`（画像表已有、人格仍不读表）

下一空 Flyway 版本：**V28**。

清单里已完成：工程底座、搭子 Phase 1（关键词危机 + 语义升高 + 干预写入/管理列表）、记忆表写入缝。

清单里还没有、且不在第一刀：运行层抽取写 MySQL、混合召回、提示词注入、真实 `SemanticRiskJudge`、PersonaConsistencyMiddleware、主动唤醒。

## 第一刀（只做这些）

新开 `agent-vector`：

- 只做集合确保 + 向量存取口。接口 + Milvus 实现 + 测试空实现。
- 依赖：`agent-common`、`milvus-sdk-java`。不依赖 runtime / state / semantic / access / admin。
- 不放：嵌入、MySQL 记忆、提示词拼装、知识切片、`retrieveQuery`、RAG 编排。
- 用户进程启动：迁走现有 `MilvusKnowledge`，再确保 `episodic_memory`。
- `knowledge` schema **不动**：`id` / `vector` / `user_id` / `agent_id`。
- `episodic_memory` 字段：`id`（MySQL 行 id 字符串）、`vector`、`tenant_id`、`user_id`、`agent_id`。维度共用 1024，COSINE + AUTOINDEX 可对齐现有 knowledge。
- 管理进程仍只 `MilvusProbe`，不要依赖本模块。
- `mvn test` 不连真实 Redis / MySQL / Milvus / Nacos。JDK 21。

## 已锁、第一刀不要实现的后继约定

- MySQL 主账本；Milvus 只召回情景摘要。画像 / 阶段 / 显著性不进向量。
- 先提交 MySQL，再嵌入写向量；失败只打日志。
- 检索：MySQL 最近 3 + 近邻 3 id，去重最多 5，显著性再时间。Milvus 挂了只用 MySQL。
- 普通/升高注入短画像 + 最多 5 条；危机不注入。
- 嵌入锁 `text-embedding-v4` / 1024；密钥不是启动门槛。
- 删除：模块可预留按 id / 按 tenant+user+agent 删；批准删除仍不执行。

## 对照文件（不要复述）

- 架构约束：`doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`
- 模块现状：`agent-bootstrap/README.md`、`agent-state/README.md`、`agent-access/README.md`
- 现有建集合：`agent-bootstrap/src/main/java/com/zhu/bootstrap/MilvusKnowledge.java`、`KnowledgeFilter.java`、`MilvusKnowledgeRunner.java`
- 搭子 Phase 1 计划（若还在）：本机 Cursor plans 目录，另一台可能没有
- 产品文档在本机 Downloads，不在仓库，不要抄进去

## 另一台机器开场白

拉到含 V27 的 `feat/agentscope-platform-base` 后说：「继续往下推进」。先读这份 handoff 和 `agent-bootstrap/README.md`，按检查清单做 `agent-vector` 一块。

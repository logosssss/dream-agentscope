# Handoff：搭子记忆 × Milvus 整合

下一场：用户说「继续往下推进」时，只做运行层抽取写 MySQL。不要做嵌入、召回、注入。不要改 `doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`。

## 下一场建议技能

无。按仓库 `.cursor/rules/continue-landing.mdc` 做一块就停。不要再开 `/grill-me`，方案已锁。

## 从哪接着

分支：`feat/agentscope-platform-base`（不要用 `master`）。

远端：`origin/feat/agentscope-platform-base`。本机有未推送提交。不要擅自 push。

**第一刀 `agent-vector` 已落地（未提交）：**

- 新模块 `agent-vector`：集合确保 + `VectorStore` 口
- `MilvusCollections.ensure` 同时确保 `knowledge` 和 `episodic_memory`
- `knowledge` 字段未改：`id` / `vector` / `user_id` / `agent_id`
- `episodic_memory` 字段：`id`、`vector`、`tenant_id`、`user_id`、`agent_id`
- 接口 + `MilvusVectorStore` + `AbsentVectorStore`
- 管理进程仍只 `MilvusProbe`，不依赖本模块
- `mvn test` 已过，不连真实 Redis / MySQL / Milvus / Nacos

记忆表写入缝（V27 + Repository）也还在工作区，一并带走。

下一空 Flyway 版本：**V28**。

## 第二刀（只做这些）

运行层抽取写 MySQL：

- 对话结束后把短摘要写入 `EpisodicMemoryRepository`
- 画像写入 `UserProfileRepository`（阶段仍先用现有约定，Persona 仍不读表）
- 失败只打日志，不挡回复
- 不接嵌入、不写 Milvus、不召回、不注入提示词
- 危机档位不写记忆

## 已锁、第二刀不要实现的后继约定

- MySQL 主账本；Milvus 只召回情景摘要。画像 / 阶段 / 显著性不进向量。
- 先提交 MySQL，再嵌入写向量；失败只打日志。
- 检索：MySQL 最近 3 + 近邻 3 id，去重最多 5，显著性再时间。Milvus 挂了只用 MySQL。
- 普通/升高注入短画像 + 最多 5 条；危机不注入。
- 嵌入锁 `text-embedding-v4` / 1024；密钥不是启动门槛。
- 删除：模块已预留按 id / 按 tenant+user+agent 删；批准删除仍不执行。

## 对照文件（不要复述）

- 架构约束：`doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`
- 向量模块：`agent-vector/README.md`
- 记忆表：`agent-state/README.md`、`EpisodicMemoryRepository`、`UserProfileRepository`
- 运行层：`agent-runtime/README.md`

## 另一台机器开场白

拉到含 `agent-vector` 的 `feat/agentscope-platform-base` 后说：「继续往下推进」。先读这份 handoff 和 `agent-runtime/README.md`，按检查清单做抽取写 MySQL 一块。

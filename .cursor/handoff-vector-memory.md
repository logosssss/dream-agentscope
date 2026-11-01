# Handoff：搭子记忆 × Milvus 整合

下一场：用户说「继续」或「继续往下推进」时，只做嵌入后写向量。不要做召回、注入。不要改 `doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`。

## 下一场建议技能

无。按仓库 `.cursor/rules/continue-landing.mdc` 做一块就停。不要再开 `/grill-me`，方案已锁。

## 从哪接着

分支：`feat/agentscope-platform-base`（不要用 `master`）。

远端：`origin/feat/agentscope-platform-base`。本机有未推送提交。不要擅自 push。

**已落地：**

- `agent-vector`：集合确保 + `VectorStore`。进程内一个 `MilvusClientV2`。
- 记忆表 V27 + Repository。
- 运行层 `CompanionMemoryMiddleware`：普通/升高在对话成功结束后写 MySQL 短摘要和相识画像。危机不写。写失败不挡回复。不调用模型，不写向量。

下一空 Flyway 版本：**V28**。

## 第三刀（只做这些）

先提交 MySQL，再嵌入写向量：

- 嵌入锁 `text-embedding-v4` / 1024
- 密钥不是启动门槛；没密钥或失败只打日志，不挡回复
- 只把刚写入的情景摘要向量写入 `episodic_memory` 集合
- `id` 用 MySQL 行 id 字符串。还要带 `tenant_id` / `user_id` / `agent_id`
- 画像 / 阶段 / 显著性不进向量
- 不召回、不注入提示词

当前 `append` 不回 id。这一刀如果要写向量，先让 MySQL 写入缝返回自增 id，不要另开表。

## 已锁、第三刀不要实现的后继约定

- 检索：MySQL 最近 3 + 近邻 3 id，去重最多 5，显著性再时间。Milvus 挂了只用 MySQL。
- 普通/升高注入短画像 + 最多 5 条；危机不注入。
- 删除：模块已预留按 id / 按 tenant+user+agent 删；批准删除仍不执行。

## 对照文件（不要复述）

- 向量模块：`agent-vector/README.md`
- 记忆表：`agent-state/README.md`、`EpisodicMemoryRepository`
- 运行层：`CompanionMemoryMiddleware`
- 嵌入约定已锁，实现还没有

## 另一台机器开场白

拉到含 `CompanionMemoryMiddleware` 的 `feat/agentscope-platform-base` 后说：「继续」。先读这份 handoff，只做嵌入写向量一块。

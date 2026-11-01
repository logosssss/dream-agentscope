# Handoff：搭子记忆 × Milvus 整合

下一场：用户说「继续」或「继续往下推进」时，只做批准删除仍不执行这一块。不要改 `doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`。

## 下一场建议技能

无。按仓库 `.cursor/rules/continue-landing.mdc` 做一块就停。不要再开 `/grill-me`，方案已锁。

## 从哪接着

分支：`feat/agentscope-platform-base`（不要用 `master`）。

远端：`origin/feat/agentscope-platform-base`。本机有未推送提交。不要擅自 push。

**已落地：**

- `agent-vector`：集合确保 + `VectorStore`。进程内一个 `MilvusClientV2`。
- 记忆表 V27 + Repository。`append` 回自增 id。`recent` / `findByIds` 按租户用户读。画像 `find` 按租户用户读。
- 运行层 `CompanionMemoryMiddleware`：普通/升高先召回再写。危机不召回也不写。
- 嵌入锁 `text-embedding-v4` / 1024。密钥读 `DASHSCOPE_API_KEY`，不是启动门槛。
- MySQL 有行 id 后把情景摘要向量写入 `episodic_memory`。
- 检索：MySQL 最近 3 + 近邻 3 id，去重最多 5，显著性再时间。Milvus 挂了只用 MySQL。结果在 `RecalledMemories`。
- 注入：普通/升高把短画像 + 最多 5 条召回追加到系统提示词。危机不追加。读失败不挡对话。Persona 仍不读表，注入从表读，没有行再用锁定阶段。

下一空 Flyway 版本：**V28**。

## 第六刀（只做这些）

删除：

- 模块已预留按 id / 按 tenant+user+agent 删
- 批准删除仍不执行

## 对照文件（不要复述）

- 召回结果：`RecalledMemories`
- 运行层：`CompanionMemoryMiddleware`
- 画像：`UserProfileRepository.find`

## 另一台机器开场白

拉到含注入的 `feat/agentscope-platform-base` 后说：「继续」。先读这份 handoff，只做批准删除仍不执行一块。

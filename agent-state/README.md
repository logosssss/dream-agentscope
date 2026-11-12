# agent-state

状态层。会话状态、工作区文件和沙箱执行锁的持久化归这里。本模块不认识产品名，也不调用模型。

坐标：`com.zhu:agent-state`。包名：`com.zhu.state`。

## 依赖

依赖 `agent-common`、Spring JDBC、MyBatis-Plus、Flyway、MySQL 驱动、`agentscope-harness`、`agentscope-extensions-mysql`。

`DistributedStore` 这个类型定义在 harness 里，所以状态层要依赖 harness。这只是存储接口，本模块不创建 `HarnessAgent`。

不能依赖 `agent-semantic`、`agent-runtime`、`agent-access`。

## 包

| 包 | 类型 | 现状 |
| --- | --- | --- |
| `api.store` | `AgentStateRepository`、`WorkspaceFileRepository`、`ExecutionLockService` | 会话状态、工作区文件、沙箱执行锁读写缝；还没有旁路实现类（走框架 `DistributedStore`） |
| `api.memory` | `EpisodicMemoryRepository`、`EpisodicMemoryItem`、`UserProfileRepository`、`UserProfileItem` | **遗留**：旧情景/画像读写缝。跨会话权威已迁 Harness 工作区 `MEMORY.md`；对话主路径不再写入。人设中间件仍可读旧行作触发 |
| `api.knowledge` | `KnowledgeRepository`、`KnowledgeBaseItem`、`KnowledgeBaseOwned`、`KnowledgeDocumentItem`、`KnowledgeDocumentVersion`、`KnowledgeChunkItem` | 知识库读写缝。向量不在这里 |
| `config` | `MysqlStoreProperties` | 前缀 `agentscope.distributed.mysql` |
| `config` | `DistributedStoreConfig` | 仅在开关打开时装配 `DistributedStore` |
| `config` | `StateMapperConfig` | MySQL 打开时扫描记忆 Mapper |
| `application` | `AbsentEpisodicMemoryRepository`、`AbsentUserProfileRepository`、`AbsentKnowledgeRepository` | MySQL 关闭时的空实现 |
| `infrastructure.persistence` | `MysqlEpisodicMemoryRepository`、`MysqlUserProfileRepository`、`MysqlKnowledgeRepository` | MySQL 打开时的实现。情景/画像为遗留缝（对话主路径不再写）；知识库表由 `V34__knowledge_base.sql` 建立 |

前三个仓库接口是给运行层以后调用的缝。当前真正装配的是框架的 `DistributedStore`，不要在旁边再写一套绕过它的 SQL。不标过时，也不先写实现。自定义查询出现再实现。

情景记忆和用户画像是搭子历史表，不走 `DistributedStore`。MySQL 打开时用 MyBatis-Plus 读写，Java 不拼 SQL；关闭时的实现直接跳过。接入层和管理进程不要调用这两个接口。**跨会话记忆权威是用户工作区 `MEMORY.md`**（见 `docs/memory-longterm-落地.md`）。对话路径已拆除 `CompanionMemoryMiddleware`，不再 append/upsert；`PersonaConsistencyMiddleware` 仍可读旧行判断跨会话恢复。表与仓储本专题不删。

## 装配

正式配置打开 `agentscope.distributed.mysql.enabled`。开关关闭时没有 `DistributedStore` Bean，只留给测试。

打开后，`DistributedStoreConfig` 用同一个 `DataSource` 接上四块：

- `MysqlAgentStateStore`：会话状态
- `JdbcStore`：工作区文件
- `JdbcSnapshotSpec`：沙箱快照
- `JdbcSandboxExecutionGuard`：执行锁

`create-if-not-exist` 必须保持 `false`。框架自带的一行工厂会默认建表，这里按配置关掉，表结构只由 Flyway 管理。用户进程和管理进程的连接池各自最大 20。

## 表

脚本：`V1__distributed_store.sql` 建框架三张表。`V27__companion_memory.sql` 建搭子记忆两张表。

| 表 | 内容 |
| --- | --- |
| `agentscope_sessions` | 会话状态。主键 `(session_id, state_key, item_index)` |
| `agentscope_store` | 工作区 KV（`item_key`/`value_json`/`version`/`updated_at`，对齐 2.0.4 JdbcStore） |
| `agentscope_sandbox_snapshots` | 沙箱快照。大对象先放 MySQL |
| `episodic_memory` | **遗留**情景记忆。主键自增，按 `(tenant_id, user_id, created_at)` 查。不存对话全文；对话主路径不再写入 |
| `user_profile` | **遗留**用户画像。同一 `(tenant_id, user_id)` 一行；对话主路径不再写关系阶段 |

字符集 `utf8mb4`。`session_id`、`state_key`、`namespace_path`、`key_name`、`snapshot_key` 使用 `utf8mb4_bin`，避免大小写被折叠后串键。

## 还没有

`AgentStateRepository`、`WorkspaceFileRepository`、`ExecutionLockService` 的实现。本地对象存储在 `agent-object-store`，不由本模块持有。工作区文件在挂上 `DistributedStore` 后走远程文件系统，进 `agentscope_store`。情景/画像表与仓储保留但对话主路径不再写入；权威见 `MEMORY.md`。知识库正文与切块已落 `kb_*`；列表、单库详情（含创建时间）、文档最新版详情与级联删除已有。文档详情不带正文。列表带建库时间。`kb_chunk` 已加 ngram FULLTEXT；仓储提供 `searchSuccessByFulltext`。Redis 只做额度、幂等和限流，不存 Agent 状态。

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
| `api` | `AgentStateRepository` | 会话状态读写缝，还没有实现类 |
| `api` | `WorkspaceFileRepository` | 工作区文件读写缝，对应 BaseStore，还没有实现类 |
| `api` | `ExecutionLockService` | 沙箱执行锁，还没有实现类 |
| `api` | `EpisodicMemoryRepository` | 情景记忆写入缝。按租户和用户切开 |
| `api` | `UserProfileRepository` | 用户画像写入缝。每个租户用户一行 |
| `config` | `MysqlStoreProperties` | 前缀 `agentscope.distributed.mysql` |
| `config` | `DistributedStoreConfig` | 仅在开关打开时装配 `DistributedStore` |
| `config` | `StateMapperConfig` | MySQL 打开时扫描记忆 Mapper |
| `application` | `AbsentEpisodicMemoryRepository`、`AbsentUserProfileRepository` | MySQL 关闭时的空实现 |
| `infrastructure.persistence` | `MysqlEpisodicMemoryRepository`、`MysqlUserProfileRepository` | MySQL 打开时写入。情景记忆走 `BaseMapper.insert`，画像覆盖写在 XML |

前三个仓库接口是给运行层以后调用的缝。当前真正装配的是框架的 `DistributedStore`，不要在旁边再写一套绕过它的 SQL。不标过时，也不先写实现。自定义查询出现再实现。

情景记忆和用户画像是搭子自己的表，不走 `DistributedStore`。MySQL 打开时用 MyBatis-Plus 写入，Java 不拼 SQL；关闭时的实现直接跳过。接入层和管理进程不要调用这两个接口。运行层会在对话成功结束后写入。`append` 成功时返回自增 id，给向量主键用。检索和提示词注入还没有。

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
| `agentscope_store` | 工作区 KV，例如 MEMORY.md、sessions、skills |
| `agentscope_sandbox_snapshots` | 沙箱快照。大对象先放 MySQL |
| `episodic_memory` | 情景记忆。主键自增，按 `(tenant_id, user_id, created_at)` 查。不存对话全文 |
| `user_profile` | 用户画像。同一 `(tenant_id, user_id)` 一行。关系阶段由运行层写 |

字符集 `utf8mb4`。`session_id`、`state_key`、`namespace_path`、`key_name`、`snapshot_key` 使用 `utf8mb4_bin`，避免大小写被折叠后串键。

## 还没有

`AgentStateRepository`、`WorkspaceFileRepository`、`ExecutionLockService` 的实现、对象存储。工作区文件在挂上 `DistributedStore` 后走远程文件系统，进 `agentscope_store`。情景记忆和画像由运行层在对话成功结束后写入，`append` 回自增 id。Java 不拼这两张表的 SQL。检索和提示词注入还没有。Redis 只做额度、幂等和限流，不存 Agent 状态。

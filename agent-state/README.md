# agent-state

状态层。会话状态、工作区文件和沙箱执行锁的持久化归这里。本模块不认识产品名，也不调用模型。

坐标：`com.zhu:agent-state`。包名：`com.zhu.state`。

## 依赖

依赖 `agent-common`、Spring JDBC、Flyway、MySQL 驱动、`agentscope-harness`、`agentscope-extensions-mysql`。

`DistributedStore` 这个类型定义在 harness 里，所以状态层要依赖 harness。这只是存储接口，本模块不创建 `HarnessAgent`。

不能依赖 `agent-semantic`、`agent-runtime`、`agent-access`。

## 包

| 包 | 类型 | 现状 |
| --- | --- | --- |
| `api` | `AgentStateRepository` | 会话状态读写缝，还没有实现类 |
| `api` | `WorkspaceFileRepository` | 工作区文件读写缝，对应 BaseStore，还没有实现类 |
| `api` | `ExecutionLockService` | 沙箱执行锁，还没有实现类 |
| `config` | `MysqlStoreProperties` | 前缀 `agentscope.distributed.mysql` |
| `config` | `DistributedStoreConfig` | 仅在开关打开时装配 `DistributedStore` |

三个仓库接口是给运行层以后调用的缝。当前真正装配的是框架的 `DistributedStore`，不要在旁边再写一套绕过它的 SQL。

## 装配

正式配置打开 `agentscope.distributed.mysql.enabled`。开关关闭时没有 `DistributedStore` Bean，只留给测试。

打开后，`DistributedStoreConfig` 用同一个 `DataSource` 接上四块：

- `MysqlAgentStateStore`：会话状态
- `JdbcStore`：工作区文件
- `JdbcSnapshotSpec`：沙箱快照
- `JdbcSandboxExecutionGuard`：执行锁

`create-if-not-exist` 必须保持 `false`。框架自带的一行工厂会默认建表，这里按配置关掉，表结构只由 Flyway 管理。

## 表

脚本：`src/main/resources/db/migration/V1__distributed_store.sql`。

| 表 | 内容 |
| --- | --- |
| `agentscope_sessions` | 会话状态。主键 `(session_id, state_key, item_index)` |
| `agentscope_store` | 工作区 KV，例如 MEMORY.md、sessions、skills |
| `agentscope_sandbox_snapshots` | 沙箱快照。大对象先放 MySQL |

字符集 `utf8mb4`。`session_id`、`state_key`、`namespace_path`、`key_name`、`snapshot_key` 使用 `utf8mb4_bin`，避免大小写被折叠后串键。

## 还没有

仓库接口的实现、Redis 缓存、对象存储。Redis 以后只做额度、幂等和限流，不拿来存 Agent 状态。

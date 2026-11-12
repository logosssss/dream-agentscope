# agent-vector

向量库。只负责集合确保和向量存取。本模块不认识产品名，也不调用模型。

坐标：`com.zhu:agent-vector`。包名：`com.zhu.vector`。

## 依赖

依赖 `agent-common`、`milvus-sdk-java`、`spring-boot-autoconfigure`。

不能依赖 `agent-state`、`agent-semantic`、`agent-runtime`、`agent-access`、`agent-admin`。

管理进程不要依赖本模块。管理进程只探测地址，不建集合。

## 包

| 包 | 类型 | 现状 |
| --- | --- | --- |
| `api` | `VectorStore` | 向量写入、近邻和删除缝 |
| `api` | `KnowledgeFilter`、`MemoryFilter` | 知识按用户和 Agent 过滤。`MemoryFilter` 给遗留情景集合加租户 |
| `config` | `VectorConfig` | 启动依赖打开时放一个 `MilvusClientV2`，停机 close |
| `application` | `AbsentVectorStore` | 启动依赖关闭时的空实现。不连 Milvus，也不改记到内存 |
| `infrastructure` | `MilvusCollections` | 用户进程启动时确保 `knowledge` 和遗留 `episodic_memory`。复用容器里的客户端 |
| `infrastructure` | `MilvusVectorStore` | 真实写入和检索。复用同一条 gRPC 通道 |

`knowledge` 字段是 `id` / `vector` / `user_id` / `agent_id`。不要改。

`episodic_memory`（遗留）字段是 `id`（MySQL 行 id 字符串）、`vector`、`tenant_id`、`user_id`、`agent_id`。维度与知识集合相同，默认 1024。度量 COSINE，索引 AUTOINDEX。对话主路径不再写入。

删除只预留按 id，以及按租户、用户和 Agent。批准删除仍不执行。

## 还没有

知识切片、索引编排和 Agentic 检索已在运行层 / 状态层落地；本模块仍提供 `knowledge` / `episodic_memory` 集合的写入与近邻。提示词拼装不在这里做。**`episodic_memory` 为遗留集合**：对话主路径已拆除 Companion 召回/写入，启动仍确保集合存在，但运行层不再写入或近邻情景向量。跨会话权威见工作区 `MEMORY.md`（`docs/memory-longterm-落地.md`）。

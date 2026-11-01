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
| `api` | `KnowledgeFilter`、`MemoryFilter` | 知识按用户和 Agent 过滤。情景记忆再加租户 |
| `config` | `VectorConfig` | 启动依赖打开时放一个 `MilvusClientV2`，停机 close |
| `application` | `AbsentVectorStore` | 启动依赖关闭时的空实现。不连 Milvus，也不改记到内存 |
| `infrastructure` | `MilvusCollections` | 用户进程启动时确保 `knowledge` 和 `episodic_memory`。复用容器里的客户端 |
| `infrastructure` | `MilvusVectorStore` | 真实写入和检索。复用同一条 gRPC 通道 |

`knowledge` 字段是 `id` / `vector` / `user_id` / `agent_id`。不要改。

`episodic_memory` 字段是 `id`（MySQL 行 id 字符串）、`vector`、`tenant_id`、`user_id`、`agent_id`。维度与知识集合相同，默认 1024。度量 COSINE，索引 AUTOINDEX。

删除只预留按 id，以及按租户、用户和 Agent。批准删除仍不执行。

## 还没有

从 MySQL 读记忆、提示词拼装、知识切片、`retrieveQuery`、RAG 编排。运行层会在 MySQL 写入成功后把情景摘要向量写入本模块，嵌入不在这里做。检索和提示词注入还没有。

# 链路图（9～13）

结论权威：[`CompanionMiddlewareChain`](../../agent-runtime/src/main/java/com/zhu/runtime/infrastructure/agent/CompanionMiddlewareChain.java)（产品闸顺序）、[`HarnessAgentFactory`](../../agent-runtime/src/main/java/com/zhu/runtime/infrastructure/agent/HarnessAgentFactory.java)、各 `docs/*-落地.md`。索引见 [README.md](./README.md)。

## 9. companion 一轮 Middleware 顺序

`configureCommon` 先挂 tracing / shutdown；`customizeCompanion` 经 `CompanionMiddlewareChain.apply` 挂产品中间件。Eviction → Compaction 须在 SafetyGate 之后；CRITICAL 整段跳过 Eviction/Compaction；Memory flush 非 CRITICAL 才入队。

```mermaid
flowchart TB
  common["configureCommon\nOtelTracing → GracefulShutdown\n+ disable Compaction/Eviction/动态 Skill/动态 Subagent\n+ MemoryConfig + PermissionContext"]
  c1["AuditMiddleware"]
  c2["FileWriteLimitMiddleware"]
  c3["ApprovalMiddleware\n产品条件闸"]
  c4["SafetyGateMiddleware\n定 RiskLevel"]
  c5["CrisisGatedToolResultEviction"]
  c6["CrisisGatedCompaction"]
  c7["PersonaConsistencyMiddleware\n纠正短句或满 20 轮"]
  c8["CrisisGatedMemoryFlush"]

  common --> c1 --> c2 --> c3 --> c4 --> c5 --> c6 --> c7 --> c8
```

echo 不挂 c1～c8 产品闸；紧急 `compactionHook` **不装**（见 12）。

## 10. Channel Gateway + SessionTurnGate

白名单 `{chatui, api}`；同 room 跨 Channel 串行靠 room 归一化闸。

```mermaid
flowchart LR
  subgraph accessPort ["access 端口"]
    Chat["Chat / ApiChat 应用服务"]
  end
  subgraph runtimeGw ["runtime"]
    Gw["ChannelGateway"]
    ChatUi["ChatUi Channel"]
    ApiCh["ApiChannel"]
    Gate["RoomNormalizedSessionTurnGate"]
    Boot["GatewayBootstrap / ChannelManager"]
  end

  Chat --> Gw
  Gw --> Gate
  Gw --> ChatUi
  Gw --> ApiCh
  Gw --> Boot
```

## 11. Memory：flush 与 CRITICAL

跨会话权威为用户工作区 `MEMORY.md`（Harness flush/consolidation，`memory_*` 已开）。情景/画像表已硬删。

```mermaid
flowchart TB
  Turn["本轮结束"]
  Risk{"RiskLevel"}
  Skip["CRITICAL：不入队 flush"]
  Flush["CrisisGatedMemoryFlush\n委托框架 flush / maintenance"]
  Daily["memory/YYYY-MM-DD.md"]
  Mem["consolidation → MEMORY.md"]
  Tools["memory_* 工具可读改"]

  Turn --> Risk
  Risk -->|CRITICAL| Skip
  Risk -->|NORMAL / ELEVATED| Flush
  Flush --> Daily --> Mem
  Tools --> Mem
```

## 12. CompactionHook 口径 C

公开 API 无法「只装 hook」：`compaction(config)` 会同时挂未闸 `CompactionMiddleware`。本仓 **不装**紧急 hook，保留 `disableCompaction` + 危机闸。

```mermaid
flowchart TB
  A["disableCompaction +\nCrisisGatedCompactionMiddleware"]
  B["compaction config\n同时装 hook + 链上 CompactionMiddleware"]
  C["口径 C：采用 A\n不装紧急 hook"]
  Spike["spike 证伪：无法只装 hook"]

  Spike --> C
  A --> C
  B -.->|不采用：双压/打穿危机闸| X["拒绝"]
```

## 13. 知识检索链路

工具 `knowledge_search`：Hybrid 合并最多 20，再 Rerank 到 5；向量失败降级全文；Rerank 失败退回 Hybrid 原序。

```mermaid
flowchart LR
  Q["查询文本"]
  Emb["嵌入"]
  Vec["向量近邻"]
  Ft["FULLTEXT ngram"]
  Hy["HybridChunkMerger"]
  Rr["ChunkReranker"]
  Out["最多 5 条 SUCCESS 切块"]

  Q --> Emb --> Vec
  Q --> Ft
  Vec --> Hy
  Ft --> Hy
  Hy --> Rr --> Out
```

# 缓存层落地（已锁定）

日期：2026-11-19。  
本文只记 grill 收口与落地顺序。不改架构指南原文。

## 一期待锁定（L1 · 已收口）

| 项 | 约定 |
| --- | --- |
| 目标 | 独立缓存层：连接统一 + 业务门面 + Redisson（A+B+C） |
| 模块 | **M1** `agent-cache` 聚合；按后端族 **S1**：`agent-cache-api` + `agent-cache-redis`（Lettuce 与 Redisson **同族**）；以后才 mongodb 等 |
| 门面 | **N3** 小接口 + **C1** `CacheFacade`；**F2** 含 Redisson `DistributedLock` |
| 失败 | **E1**：失败 → `UNAVAILABLE`/503；禁止内存回落；`enabled=false` 时由调用方替身 |
| 迁移 | **Mig1** 直接迁走 access 的 `AccessCache` 实现 |
| 依赖 | **P1**：access/admin/runtime/product 可依赖 **api**；禁止依赖 redis 实现包 |
| 键 | **K4**：`CacheKeys` 统一前缀 + 可选租户段 |
| 配置 | 沿用 `agentscope.cache.redis.*` |

### 一期明确不做

1. 不建 mongodb / caffeine 等其它族模块  
2. 一阶段不改 runtime/product **真实调用点**（只保证可依赖 api）  
3. 不改额度/限流业务公式  
4. 不引入内存后端；不做 Redis 服务发现  
5. 不改架构指南正文  

### 一期落地顺序（已完成）

1. **① 建模块** — 聚合 + api + redis；锁文；根 POM；单测 Keys。  
2. **② 迁 access** — 删旧 `AccessCache` 等；调用点改新接口/门面；测试替身。  
3. **③ admin + ArchUnit** — 去重建厂；禁 semantic/state/vector→cache；product/runtime 仅 api。  
4. **④ 文档** — README / packaging / 图册短记。  

---

## 二期已锁定（全项目扫描 · Redis 能力替换）

日期：2026-11-19。  
范围：正确性 + 性能读缓存 + 原语 api 占位（清单式覆盖 A/B/C）。  
落地序轴：正确性 →（旧 AccessCache 磁盘已无，核对即可）→ 读缓存 → 原语形状 → 文档。

| 项 | 约定 |
| --- | --- |
| PendingAsk | **本轮不迁**；单 JVM / 粘滞为已知限制；多副本另专题 |
| Wakeup Bus | **不进 Redis**（见 `docs/wakeup-落地.md`） |
| Agent 注册表 / Drain / Gateway 锁 | **留 JVM**；不进 Redis |
| Wx `access_token` | → `CacheFacade.kv` + TTL；提前刷新窗口保留；不先上分布式锁防双刷 |
| 读缓存对象 | 租户/用户 **ACTIVE 门** + **额度上限**（`TenantQuotaLookup`） |
| 读缓存放置 | **audit 内嵌**（`MysqlTenant*` / Gate 实现依赖 **cache-api**）；写路径尽力删键 |
| 读缓存失败 | Redis 失败/超时 → **直查 MySQL**（与额度计数/占锁的 E1/503 **分流**） |
| TTL | ACTIVE 门 **30s**；额度上限 **60s**；跨进程靠 TTL 兜底；不做 Pub/Sub |
| 原语扩 api | `RateLimiter` / Bloom（或等价）进 **api + redis 实现**；**不改**额度/限流业务公式；**本轮不接线** |
| 依赖补丁 | 二期允许 **audit → cache-api**；仍禁止 audit/access/runtime/product → `cache.redis`；semantic/state/vector/objectstore ↛ cache |
| 文档 | 本文追加二期；不另建 expand 文 |

### 二期明确不做

1. PendingAsk / 框架 `pendingAsk` 状态外置；多副本粘滞路由  
2. Wakeup MessageBus 换 Redis；RocketMQ / `mode=mq`  
3. HarnessAgentRegistry 等 JVM 生命周期进 Redis  
4. 进程内内存长缓存顶 Redis 失败  
5. 本轮接 RRateLimiter/Bloom 到 `QuotaGuard` 或改分钟限流公式  
6. semantic 工具目录/权限行 Redis 化（ArchUnit semantic ↛ cache）  
7. 会话详情/消息列表短缓存（后置）  
8. 不改架构指南正文  

### 二期落地顺序（一次一块）

1. **① 锁文档** — 本块：扩写本文二期表与顺序。  
2. **② Wx token** — `WxWeixin.accessToken` 改 `CacheFacade.kv`；键进 `CacheKeys`；单测。  
3. **③ audit 读缓存** — audit 依赖 cache-api；ACTIVE 门 / 额度上限短 TTL + 写删键；失败直查 MySQL；ArchUnit 允许 audit→api、仍禁→redis；单测。  
4. **④ 原语形状** — api 增 RateLimiter / Bloom（或等价）+ `CacheFacade` 暴露；redis 实现与单测；**不接线**。  
5. **⑤ 文档收口** — README / packaging「缓存层拓扑」/ 图册短记对齐二期。  

## 状态

一期 **①～④ 已收口**。二期 grill 已结束；**①～⑤ 已全部收口**（含 README / packaging「缓存层拓扑」/ 图册图 2）。若再改规范，先改本文再动代码。

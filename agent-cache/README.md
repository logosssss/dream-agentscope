# agent-cache

独立缓存层聚合。业务只依赖 `agent-cache-api`；Redis 族实现在 `agent-cache-redis`（Lettuce + Redisson）。

锁定见 [`docs/cache-落地.md`](../docs/cache-落地.md)。

## 子模块

| 模块 | 职责 |
| --- | --- |
| `agent-cache-api` | N3 小接口、`CacheFacade`、`CacheKeys`；含 RateLimiter / Bloom 形状 |
| `agent-cache-redis` | 连接与实现（Lettuce + Redisson）；配置前缀 `agentscope.cache.redis` |

## 依赖方向

- access / runtime / product / **audit** → **api**（不碰 redis 实现）  
- 仅 `agent-bootstrap` / `agent-admin` 引入 **redis 实现** 做建连与装配  
- semantic / state / vector / objectstore 禁止依赖 cache（ArchUnit）

拓扑短记：[`docs/packaging-落地.md`](../docs/packaging-落地.md)「缓存层拓扑」；图册图 2：[`docs/diagrams/architecture.md`](../docs/diagrams/architecture.md)。一期 + 二期锁定：[`docs/cache-落地.md`](../docs/cache-落地.md)。

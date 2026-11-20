# agent-cache-redis

Redis 族实现：Lettuce（计数 / KV / TokenLock）+ Redisson（`DistributedLock` / `RateLimiter` / `BloomFilter`）。  
配置前缀：`agentscope.cache.redis`（`enabled` / `host` / `port` / `password`）。

业务代码依赖 [`agent-cache-api`](../agent-cache-api)；本模块由 bootstrap / admin 装配引入。  
`RateLimiter` / `BloomFilter` 已装配，默认无业务接线（见 `docs/cache-落地.md` 二期）。

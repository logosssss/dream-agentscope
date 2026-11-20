# agent-cache-api

缓存契约：`CounterCache` / `KvCache` / `TokenLock` / `DistributedLock` / `RateLimiter` / `BloomFilter`、`CacheFacade`、`CacheKeys`。  
`RateLimiter` / `BloomFilter` 为二期形状，默认不接线业务额度。无中间件依赖。实现见 `agent-cache-redis`。

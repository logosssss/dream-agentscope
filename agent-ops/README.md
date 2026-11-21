# agent-ops

两个进程共用的运营行为。

## 为什么有这个模块

`agent-access`（用户进程）与 `agent-admin`（管理进程）原先各写一份「租户目录、用户目录、额度上限、待办、附件目录、删除请求、用量读侧」，
45 个类名相同、内容几乎逐行相同（归一包名后 42 对 diff 为 0）。同一张表的读法出现两份，任何一边改语义，另一边不会跟着变，
线上就会出现「管理端看到的状态和用户端不一样」。

现在只留一份：数据形状在 [`agent-readmodel`](../agent-readmodel/README.md)，行为在这里，两个进程各加一个依赖。

## 依赖与禁令

只依赖：

- `com.zhu:agent-readmodel`（entity / Mapper / XML）
- `com.zhu:agent-cache-api`（`CacheFacade`、`CacheKeys`）
- `com.zhu:agent-common`（异常、令牌、健康快照）
- MyBatis-Plus 与 Spring 注解

`ArchitectureTest.OPS_MUST_NOT_DEPEND_ON_BUSINESS` 保证它不依赖 `access` / `admin` / `runtime` / `product` / `state` / `vector` / `object-store`，
也不碰 `com.zhu.cache.redis..` 与 `io.agentscope..`。要在这里用 Redis 的具体实现，先改成在 `agent-cache-api` 里加抽象。

## 包

| 包 | 内容 |
| --- | --- |
| `com.zhu.ops.tenant` | 租户目录与状态闸门、租户用户目录与闸门、客户端登录凭据、登录名占用 |
| `com.zhu.ops.quota` | 租户额度读写（`TenantQuotaCatalog` / `MysqlTenantQuota`）、用量写入 |
| `com.zhu.ops.attachment` | 附件目录、引用、摘要 |
| `com.zhu.ops.approval` | 审批结论、通知、用户待办 |
| `com.zhu.ops.deletion` | 数据删除请求登记、读回、管理端近期列表 |
| `com.zhu.ops.usage` | token 用量读侧 |
| `com.zhu.ops.health` | MySQL 就绪探测 |
| `com.zhu.ops.support` | `QueryLimits`（分页上限）、`ReadThroughCache`（读穿缓存的小工具） |

## 使用约定

- 这里只写「怎么读写」，不写「什么时候该删」。策略留在进程模块（例如删除执行器在 `agent-access`，批准在 `agent-admin`）。
- 需要缓存时用 `ReadThroughCache` 与 `CacheKeys`，不要直接注入 Redis 客户端。
- 新增共用行为前先问：两个进程都要用吗？只有一边用就留在那一侧。

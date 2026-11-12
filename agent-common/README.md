# agent-common

公共基础。异常、通用数据结构和请求级上下文放在这里。本模块没有 Spring Bean，也没有业务语义。

坐标：`com.zhu:agent-common`。包名：`com.zhu.common`。

## 依赖

不依赖其它业务模块，也不依赖 AgentScope。任何模块都可以依赖它。它不能依赖 `agent-state`、`agent-vector`、`agent-semantic`、`agent-runtime`、`agent-audit`、`agent-access`。

## 包

| 包 | 内容 | 用途 |
| --- | --- | --- |
| `exception` | `ErrorCode`、`BusinessException` | 可预期失败用稳定错误码，不靠异常文案做分支 |
| `dto` | `ApiResult`、`PageQuery`、`PageResult` | 接入层统一响应和分页。页码从 1 开始 |
| `context` | `TenantContext`、`UserContext`、`RequestIdContext` | 当前线程的租户、用户和幂等标识。请求结束必须 `clear()`，避免线程池串话 |
| `util` | `Ids` | 还没有框架返回值时生成无连字符的随机 ID |
| `security` | `CallerTokens`、`WakeupTokens` | 比较调用方令牌 / 内部唤醒令牌。期望值由各进程配置，不放进本模块 |
| `health` | `ReadinessSnapshot`、`MilvusProbe` | 就绪结果汇总。探测只看地址通不通。建集合在 `agent-vector` |

## 使用约定

- 身份以方法参数和 `RuntimeContext` 为准。`TenantContext` / `UserContext` / `RequestIdContext` 只是接入层的线程副本，不能当作跨层传递的唯一来源。
- `ApiResult` 成功时 `code` 为 `OK`，失败时为 `ErrorCode` 的名称。
- 不要在本模块加 `@Service`、控制器或数据库访问。

## 还没有

日志门面、JSON 工具、时间工具。需要时再加，不要把某个产品的常量放进来。

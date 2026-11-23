# agent-product-api

产品契约的唯一定义处。`ProductModule` 把一个产品「对运行层可见的东西」收成一个接口：标识、工具、完成判据、提示词兜底，以及装进 Harness 与挂工具的两个钩子。

坐标：`com.zhu:agent-product-api`。包名：`com.zhu.product.api`。

## 依赖

只依赖 `com.zhu:agent-semantic`（契约层）与 `io.agentscope:agentscope-harness`（装配钩子要 `HarnessAgent.Builder`）。不依赖 `agent-runtime`、`agent-access`、`agent-admin`、`agent-products`、`agent-state`、`agent-cache`。

## 为什么有这个模块

原先有两个几乎同形的缝：

- `agent-semantic` 的 `ProductSemanticProvider`：运行层能看见的产品语义（agentId / 工具 / 完成判据 / 提示词兜底）。
- `agent-runtime` 的 `ResidentAgentBinding`：怎么把产品装进 Harness（customize / attachTools），以及它是不是 main。

结果是同一件事要在两个接口、两个实现类、两个 Bean 里各说一遍；想加一个产品要先理解「哪半在 semantic、哪半在 runtime」。现在合成一个 `ProductModule`：一个产品 = 一个实现 = 一个 Bean。

## 契约

```java
public interface ProductModule extends ProductSemanticProvider {
    boolean mainAgent();
    void customize(HarnessAgent.Builder builder);
    default void attachTools(HarnessAgent agent) { }
}
```

- `agentId()`：状态命名空间，也是 Gateway 路由键；不得为空、不得重复、不得使用平台保留的 `echo`。
- `mainAgent()`：一个进程恰好一个 main。WEB 通道恒走它；API 通道才可以点名的其它产品。
- `tools()` / `completion()` / `promptFallback(key)`：沿用语义契约，运行层只读。
- `customize(builder)`：挂模型、中间件、工作区、最大迭代数等。
- `attachTools(agent)`：Builder 阶段拿不到 `HarnessAgent` 的收尾工作（例如需要注册表的工具）。

人格（`PersonaContract`）**不在**契约里：它是平台级语义，由运行层单独注入，避免一个产品出现两份身份。

## 约定

- 一个进程可以有多个 `ProductModule`，但恰好一个 `mainAgent()`；`ProductModuleRegistry` 在启动期校验，不合法直接失败。
- 产品实现放在 `agent-products/agent-product-*`；`agent-product-api` 只放契约，不放实现。
- ArchUnit 守两条线：`agent-runtime` 只能看到 `com.zhu.product.api`（看不到具体产品）；`ProductSemanticProvider` 的实现只允许出现在 `com.zhu.product..`。
- 新增产品：新建 `agent-product-xxx` 模块，实现 `ProductModule`，注册进 `agent-products` 的聚合依赖即可，不需要改 `agent-runtime`。

## 还没有

- 产品级 quota / 计费策略（现在在 `agent-access` 的 `QuotaGuard`，按租户与用户算）。
- 运行时按产品切换模型档位（`model` 仍来自 `agentscope.runtime.model`）。
- 产品目录接口（`GET /api/platform/agents`）。前端 registry 目前手写，见 P2 文档的「明确不做」。

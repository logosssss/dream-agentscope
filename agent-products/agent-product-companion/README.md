# agent-product-companion

搭子（companion）产品域。提供人格、产品语义实现、回声工具与长驻 `ResidentAgentBinding`。

坐标：`com.zhu:agent-product-companion`。根包：`com.zhu.product.companion`。  
路径：`agent-products/agent-product-companion`（聚合父见 `agent-products/`）。

## 依赖

依赖 `agent-semantic`（契约 / api）与 `agent-runtime`（Binding 缝、中间件零件、MCP/Sandbox 工厂）。  
**禁止**被 `agent-runtime` / `agent-semantic` 反向依赖。

## 包（按域拆，后续产品照此）

| 包 | 类型 | 作用 |
| --- | --- | --- |
| `persona` | `CompanionPersona` | 锁定人格；实现 `PersonaContract` |
| `semantic` | `PlatformSemanticProvider` | `ProductSemanticProvider` 默认实现（搭子） |
| `tool` | `EchoTextTool` | 目录业务工具 `echo_text` |
| `binding` | `CompanionResidentAgentBinding` | 长驻 main Agent 装配（C1 组装 `CompanionMiddlewareChain`） |

## 不做

- 不提供 HTTP；不替代 `agent-bootstrap` 进程壳
- 不迁 `KnowledgeSearchTool`（暂留 runtime）
- 不做第二产品模块

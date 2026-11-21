# 工程护栏 P2：`ProductModule` 契约合并（已完成）

状态：**已完成**（2026-11-21）。

日期：2026-11-21。
背景：一个产品要在两个地方各说一遍——`agent-semantic` 的 `ProductSemanticProvider`（运行层能看见的语义）与 `agent-runtime` 的 `ResidentAgentBinding`（怎么装进 Harness）。
两个接口的实现类在 `agent-products/agent-product-companion`，同一个类还要实现两份契约；加一个产品得先弄懂「哪半在 semantic、哪半在 runtime」。
同时 `ChatRequest.agentId` 在 WEB 通道被原样传进执行层，但 Gateway 的 WEB 入口并不读它，字段语义与行为不一致。
本专题把两个缝合成一个契约，并把「WEB 恒走 main」写实。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 新模块 | `agent-product-api`，坐标 `com.zhu:agent-product-api`，包名 `com.zhu.product.api`；在根 `pom.xml` 的 `agent-semantic` 之后 |
| 依赖 | 只依赖 `com.zhu:agent-semantic` 与 `io.agentscope:agentscope-harness`（装配钩子需要 `HarnessAgent.Builder`）；不依赖 runtime / access / admin / products |
| 契约 | `interface ProductModule extends ProductSemanticProvider`：`boolean mainAgent()`、`void customize(HarnessAgent.Builder)`、`default void attachTools(HarnessAgent)`，加继承来的 `agentId()` / `tools()` / `completion()` / `promptFallback(key)` |
| 人格 | `PersonaContract` **不并入**：它是平台级语义，运行层单独注入，避免一个产品两份身份 |
| 注册 | `agent-runtime` 的 `ProductModuleRegistry`（`@Component`）收 `List<ProductModule>`，启动期校验：非空、agentId 非空白、不得用平台保留 `echo`、agentId 唯一、恰好一个 main |
| 路由写实 | WEB 通道恒走 `main()`，请求里的 `agentId` 不参与路由；API 通道可点名，但必须命中已注册模块，否则 `404 unknown agentId: <id>` |
| 删除 | `ResidentAgentBinding`、`ResidentAgentBindingRegistry` 及其测试；`PlatformSemanticProvider` 与它的测试（用 `CompanionSemantics` 替代） |
| 保持 | `CompanionResidentAgentBinding` 的类名与文件名不变（多个 wiring/spike 测试按文件名读源码），只改 `implements` 与构造参数 |

## 明确不做（本专题）

1. **X1** 不做产品目录接口（`GET /api/platform/agents`）。前端 registry 仍手写；WEB 恒走 main，前端 `agentId` 只用于本地会话键 `agentId:sessionId` 与路由，不参与后端路由，所以暂时没有「前端列出后端不存在产品」的风险。等真有第二个产品对用户开放再引入。
2. **X2** 不做按产品的模型档位、额度、计费策略。`model` 仍来自 `agentscope.runtime.model`，额度仍在 `agent-access` 的 `QuotaGuard`。
3. **X3** 不改 `agent-semantic` 的契约包位置：`ProductSemanticProvider` 继续留在 `com.zhu.semantic.contract.product`，`ProductModule` 继承它而不是替换它。
4. **X4** 不动 Harness 的装配语义：middleware 顺序、`disableWebTools`/`disableShellTool`、filesystem 挂载原样保留。
5. **X5** 不给 echo 探针做 `ProductModule`：它是平台自检，走 `HarnessAgentFactory.customizeEcho`，注册表显式禁止占用该 id。

## 改动（已完成）

1. 新建 `agent-product-api` 模块（`ProductModule` + `package-info` + README），根 POM 注册模块与 `dependencyManagement`。
2. `agent-runtime`：删 `ResidentAgentBinding` / `ResidentAgentBindingRegistry`；新增 `ProductModuleRegistry`；`ChannelGateway` 的构造参数、字段与 `startGatewayLocked()` 改为遍历 `products.all()`、`.mainAgent(main.agentId())`；`executeApi` / `streamApi` 走 `requireRegisteredAgent(...)`；`HarnessAgentFactory` javadoc 指向新契约。
3. `agent-products/agent-product-companion`：`CompanionResidentAgentBinding implements ProductModule`；抽出 `CompanionSemantics`（`implements ProductSemanticProvider`，空值兜底 companion-v1 / 20）；删除 `PlatformSemanticProvider`。
4. `agent-access`：`ChatRequest.agentId` javadoc 改为「仅 API 通道生效」；`ChatAppService.toRequest` 恒传 `null`（WEB 恒为 main）。
5. 测试：新增 `ProductModuleRegistryTest`（6 例）、`CompanionSemanticsTest`（2 例）；删 `ResidentAgentBindingRegistryTest`、`PlatformSemanticProviderTest`。
6. ArchUnit：`RUNTIME_MUST_NOT_DEPEND_ON_PRODUCT` → `RUNTIME_MAY_ONLY_SEE_PRODUCT_API`（runtime 只许看 `com.zhu.product.api`）；新增 `PRODUCT_SEMANTIC_IMPLEMENTED_ONLY_IN_PRODUCTS`（`ProductSemanticProvider` 的实现只许在 `com.zhu.product..`）。

## 验收与实测

| 检查 | 结果 |
| --- | --- |
| `mvn -B -ntp -Pstrict -DskipTests compile` | 通过（18 个模块，含新 `agent-product-api`） |
| `mvn -B -ntp -Pstrict -pl agent-bootstrap -am -Dtest=ArchitectureTest test` | 2 个用例通过（含新契约规则） |
| `mvn -B -ntp -Pstrict test "-Dtest=!OssObjectStoreTest"` | **430 个用例，0 失败 0 错误** |
| `ProductModuleRegistryTest` | 6 个用例通过（单 main / 缺 main / 多 main / 重复 id / 占 echo / 空集合） |
| 契约残留扫描 | `ResidentAgentBinding`、`PlatformSemanticProvider` 在 `src/` 已无引用 |
| WEB 通道行为 | `ChatAppService.toRequest` 恒传 `null`；`ChannelGateway.execute` 只取 `products.main()` |

## 仍未做 / 已知残留

1. 前端 registry（`web/src/platform/registry/index.ts` 的三行手写 `agentList`）与后端 `ProductModule.agentId()` 没有自动对齐；等 X1 打开时再一起做。
2. `docs/diagrams/architecture.md` 的模块图已加入 `agent-product-api` / `agent-readmodel` 节点与依赖边；`docs/packaging-落地.md`、`agent-bootstrap/README.md`、`README.md` 的缝描述已同步。
3. `agent-access` 的 API 通道目前只有探针级测试覆盖（`unknown agentId` 走的是 `ProductModuleRegistry.find` 的单测），没有 HTTP 层用例。
4. `agent-product-api` 没有测试：契约是纯接口，行为由 `ProductModuleRegistryTest` 与 `CompanionSemanticsTest` 覆盖。

## 后续块

P3 [`docs/engineering-p3-testing-落地.md`](./engineering-p3-testing-落地.md)（`GateChain` 收口闸门顺序、`agent-test-support` 替身、access 首批单测）已完成 → P4 配置拆分校验与文档 status/链接检查。

# P3 落地：闸门顺序、可测单元、测试替身

状态：**已完成**（2026-11-21）。承接 [`engineering-p2-product-module-落地.md`](./engineering-p2-product-module-落地.md)。

## 为什么是这三件

P2 结束时，`agent-access` 仍然是全仓**唯一没有测试**的业务模块，而它恰好是四个对话入口各自抄一遍闸门顺序的地方：
`requireUser → 租户 ACTIVE → 租户用户 ACTIVE → 会话归属 → 额度 → 并发名额`。
顺序写错只有在「租户被停用但额度已扣」这类场景才会暴露，且 bootstrap 的集成测试跑一轮要几十秒，
写不了几条。所以 P3 的顺序是：先把顺序收成一个可单测的单元，再给它写测试，再补跨模块的替身。

## 已锁定

| 项 | 决定 |
|----|------|
| 闸门顺序 | 只在 `GateChain.enter(...)` 里出现一次；四个入口（WEB 同步/流式、API 同步/流式）都调它 |
| 端口形状 | `TenantGate` / `TenantUserGate` / `SessionOwnerGate` / `QuotaGate` / `ConcurrentGate` 五个窄接口，各一个方法 |
| 接入方式 | 静态方法而不是 `@Service`：`ChatAppService` / `ApiChatAppService` 的构造签名不变，bootstrap 里 9 处 `new ChatAppService(...)` 不用改 |
| 并发名额 | `ConcurrentGate.open` 返回 `Slot`；`ConcurrentRunGuard.Slot` 实现 `ConcurrentGate.Slot`（`key()`），`close` 只认端口类型 |
| 额度 | `QuotaGuard implements QuotaGate`，`check(tenantId, userId, action, requestId)` 原签名不动 |
| 未登录 | `GateChain` 自己拦空 `userId`，抛 `BusinessException(UNAUTHORIZED, "login is required")`；入口不再各写一份 |
| 测试替身 | 新模块 `agent-test-support`，`test-jar` 打包，测试类路径专用，不进部署包 |
| 降级启动 | **不新增**：`agent-bootstrap` 的 `BootstrapContextTest` 已是「MySQL + Redis + Milvus + Harness 全关」的启动与端点矩阵 |

## 明确不做

- **X1**：不做 Testcontainers 端到端。本机没有 Docker，写了也验证不了；CI 的 `verify` job 目前也没有 service 容器。
- **X2**：不抽 Spring slice 基类。唯一的上下文测试在 `agent-bootstrap`（它要扫真实 `com.zhu` 全包），抽出来没有第二个使用者。
- **X3**：不迁移 bootstrap 既有的 120+ 测试。它们多数是「装配/契约/边界」，迁到各模块会把 bootstrap 的 `scanBasePackages` 语义拆散。
- **X4**：不把 `GateChain` 做成可注入的 Bean，也不引入责任链框架。
- **X5**：不在 `agent-test-support` 里放断言工具或 Mockito 扩展。

## 改动

1. 新增 `agent-access/.../chat/GateChain.java`（`final`、私有构造、`public static ConcurrentGate.Slot enter(...)`）。
2. 新增 `agent-access/.../chat/QuotaGate.java`、`ConcurrentGate.java`（含嵌套 `Slot`）。
3. `QuotaGuard`、`ConcurrentRunGuard` 改成实现对应端口；`Slot` 实现嵌套接口。
4. `ChatAppService`、`ApiChatAppService` 四个入口改用 `GateChain.enter(...)`；删掉各自的 `requireUser` 与 `RequestIdContext` import。
5. 新增 `agent-access/src/test/java/com/zhu/access/application/chat/GateChainTest.java`（4 例）。
6. `agent-access/pom.xml` 加 `spring-boot-starter-test`（test scope），本模块第一次有测试。
7. 新增模块 `agent-test-support`：`src/test/java/com/zhu/testsupport/StubProductModule.java`，根 pom 的 `<modules>` 与 `dependencyManagement` 各加一条；`agent-runtime` / `agent-access` / `agent-bootstrap` 以 `type=test-jar, scope=test` 引用。
8. `ProductModuleRegistryTest` 的匿名桩换成 `StubProductModule`（6 例不变）。
9. `ArchitectureTest` 的 `PRODUCT_SEMANTIC_IMPLEMENTED_ONLY_IN_PRODUCTS` 排除 `com.zhu.testsupport..`：替身不是产品实现，但它会被 ArchUnit 扫到。
10. README 模块表加 `agent-test-support` 行；`agent-access/README.md` 记 `GateChain` 与首个单测。

## 验收实测

| 验收项 | 结果 |
|--------|------|
| `mvn -B -ntp -Pstrict -pl agent-access -am test` | BUILD SUCCESS，模块 4 例、4.1s（要求 <30s） |
| `GateChainTest` | 4 例：顺序、action 透传、未登录拦截、任一闸抛错即停 |
| `ProductModuleRegistryTest` | 6 例，改用 `StubProductModule` 后不变 |
| 全量 `mvn -B -ntp -Pstrict test "-Dtest=!OssObjectStoreTest"` | 514 用例，0 失败 0 错误（其中 `agent-bootstrap` 430、`agent-admin` 38） |
| `mvn -B -ntp -Pstrict verify "-Dtest=!OssObjectStoreTest"` | BUILD SUCCESS（18 模块，含 repackage） |
| `agent-test-support` 单独构建 | BUILD SUCCESS（无自有用例，surefire 走 JUnit 5 provider） |

## 残留

- `agent-semantic`、`agent-state`、`agent-cache`、`agent-products`、`agent-common` 仍然没有测试；它们要么是纯数据形状，要么只有 spring 装配，优先级低于 access。
- 端到端（Flyway → 登录 → 一轮 chat → run/message 落库）仍未自动化，见 X1。
- `GateChainTest` 只覆盖顺序与短路，不覆盖 `QuotaGuard` 的额度计算细节（那部分仍由 bootstrap 的 `QuotaGuardTest` 覆盖）。

## 下一块

P4：配置拆分与语义校验（`AgentRuntimeProperties` 拆 Mcp/Sandbox/Wakeup、`@Validated` 边界、Nacos 推送非法值回滚），以及文档 status/索引/链接检查。

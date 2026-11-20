# agent-products

产品模块聚合（reactor）。各 `agent-product-*` 列在本 POM 的 `<modules>`；坐标与插件版本仍由根 `dream-agentscope` 管理（子模块 `parent` 仍指根，F1/D1）。

## 拓扑（已锁定：P3 / A1）

| 项 | 约定 |
| --- | --- |
| 进程 | **单用户进程**：仍只跑 `agent-bootstrap`；管理进程不依赖本聚合 |
| classpath | bootstrap 依赖需要的 `agent-product-*`；Spring 扫 `com.zhu` 挂 Binding |
| 挂载 | Gateway / 工厂经 `ResidentAgentBinding` 注册表按 `agentId` 挂长驻 Agent；**echo 仍平台**（E1） |
| semantic | 只留契约 / `api` / 共享权限·提示词·目录（S1）；产品实现不回灌 |
| 分包 | 根包只放 `package-info`；域包如 `persona` / `semantic` / `tool` / `binding` |

当前子模块：

- `agent-product-companion` — 搭子（`persona` / `semantic` / `tool` / `binding`）

后续新产品模块沿用同一分包与依赖方向；**本轮不做第二产品**。

## 边界（ArchUnit，见 `ArchitectureTest`）

- `runtime` ↛ `com.zhu.product..`（产品经 Binding 注册表挂入）
- `com.zhu.product..` ↛ `access`
- `semantic` ↛ `com.zhu.product..`（勿与 `contract.product` 包名混淆）

图：[`docs/diagrams/architecture.md`](../docs/diagrams/architecture.md)（图 2～3）。锁定摘要：[`docs/packaging-落地.md`](../docs/packaging-落地.md)#多产品拓扑。

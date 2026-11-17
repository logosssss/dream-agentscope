# 架构图（1～3）

结论权威：现码 / 根 [`README.md`](../../README.md) / 各落地文。索引见 [README.md](./README.md)。

## 1. 部署拓扑

双进程、双前端与中间件关系。用户进程执行 Flyway 与对话；管理进程不跑对话。

```mermaid
flowchart LR
  Browser["浏览器"]
  Web["web :5173"]
  WebAdmin["web-admin :5174"]
  Nginx["Nginx 可选\n代填 X-Caller-Token"]
  User["用户进程\nagent-bootstrap :8080"]
  Admin["管理进程\nagent-admin :8100"]
  MySQL[(MySQL)]
  Redis[(Redis)]
  Milvus[(Milvus)]
  Nacos[(Nacos\nAGENT_PLATFORM)]
  Model["模型服务\n如 DashScope"]

  Browser --> Web
  Browser --> WebAdmin
  Browser --> Nginx
  Web --> User
  WebAdmin --> Admin
  Nginx --> User
  User --> MySQL
  User --> Redis
  User --> Milvus
  User --> Nacos
  User --> Model
  Admin --> MySQL
  Admin --> Redis
  Admin --> Milvus
  Admin --> Nacos
```

要点：两进程启动均须连上 MySQL / Redis / Milvus；配了 `NACOS_ADDR` 须拉到 infra + 业务两份配置。

## 2. 模块依赖方向

Maven 模块与 ArchUnit 禁向（可执行规则在 `ArchitectureTest`）。箭头表示「允许依赖」；红叉为禁止方向（示意）。

```mermaid
flowchart TB
  bootstrap["agent-bootstrap"]
  admin["agent-admin"]
  access["agent-access"]
  runtime["agent-runtime"]
  semantic["agent-semantic"]
  audit["agent-audit"]
  state["agent-state"]
  vector["agent-vector"]
  objectstore["agent-object-store"]
  common["agent-common"]

  bootstrap --> access
  bootstrap --> runtime
  bootstrap --> semantic
  bootstrap --> audit
  bootstrap --> state
  bootstrap --> vector
  admin --> audit
  admin --> common
  access --> runtime
  access --> semantic
  access --> audit
  access --> common
  runtime --> semantic
  runtime --> state
  runtime --> vector
  runtime --> audit
  runtime --> common
  semantic --> common
  audit --> common
  state --> common
  state --> objectstore
  vector --> common
  objectstore --> common
```

ArchUnit 要点（禁止）：

- `semantic` ↛ `runtime` / `access`
- `runtime` ↛ `semantic.application` / `semantic.infrastructure`（只碰 contract）
- `state` / `vector` / `objectstore` ↛ 业务包（语义/运行/接入等，细则见测试）
- `access` ↛ `io.agentscope..`；`access` ↛ `runtime.infrastructure`
- `access.api.controller` ↛ `infrastructure.persistence`

## 3. 管理进程边界

管理进程不依赖运行层、接入层、Harness；不执行 Flyway；不跑对话模型链路。

```mermaid
flowchart TB
  subgraph userProc ["用户进程 agent-bootstrap"]
    accessU["agent-access"]
    runtimeU["agent-runtime + Harness"]
    flyway["Flyway"]
    chat["对话 / SSE / PendingAsk"]
  end
  subgraph adminProc ["管理进程 agent-admin"]
    adminApi["租户 / 额度 / 工具权限 / 审计查询"]
    noRuntime["不依赖 runtime / access / Harness"]
  end
  MySQL[(MySQL)]
  Redis[(Redis)]
  Milvus[(Milvus)]

  accessU --> runtimeU
  runtimeU --> chat
  flyway --> MySQL
  userProc --> MySQL
  userProc --> Redis
  userProc --> Milvus
  adminProc --> MySQL
  adminProc --> Redis
  adminProc --> Milvus
  adminApi -.->|禁止| runtimeU
```

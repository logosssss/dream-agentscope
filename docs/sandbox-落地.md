# Sandbox 落地（已锁定 · 窄范围）

日期：2026-11-12。  
本文只记 grill 收口结论与落地顺序。不改架构指南原文。  
背景：Memory / Compaction / Eviction / Skills（窄）/ Permission Gate / Subagent（窄）/ MCP（窄）已收口；优化主轴仍为 **框架原生对齐（学习向）**。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 优化主轴 | **A**：继续框架原生对齐；不上线压力；多 Channel / mq / Wakeup 仍后置 |
| 原生面 | **Sandbox（窄）**：companion 可选 `DockerFilesystemSpec`；默认仍 `RemoteFilesystemSpec` |
| 默认姿态 | **默认关**（Remote + USER）；显式 `enabled=true` 才进 Docker |
| 隔离 | **保持 USER**（与 `WorkspaceFilesystem.userScoped()` 同隔离语义） |
| Shell | **Sandbox 打开仍 `disableShellTool`**；本专题不开放宿主机/容器 shell 工具 |
| 装配范围 | **仅 companion**；主对话与 echo 仍 Remote（或沿用现工厂路径） |
| 硬失败 | `enabled=true` 但 **Docker 不可用或 DistributedStore 缺失** → **启动/重建失败**；**不**静默回退 Remote |
| 快照 / 守卫 | **复用** DistributedStore 已挂的 `JdbcSnapshotSpec` + `JdbcSandboxExecutionGuard`；不新开存储 |
| 体积 | **沿用**现有 `max-write-bytes` + 框架/镜像默认；本专题**不加**沙箱专用体积旋钮 |
| 配置源 | `agentscope.runtime.sandbox.*` 进 **业务 yml + Nacos 白名单可热更**；Docker socket / 拉取凭证等密钥 **不进 Nacos 明文** |
| 优先级 | 仍 **环境变量 > Nacos > 本地 yml**；非法热更保留上一份 |
| 默认镜像 | 文档给 **推荐默认 image tag**；本地/Nacos 可覆盖；本专题不做私有仓库拉取逻辑 |
| 可观测 | **只打结构化日志**（enabled / image / 硬失败原因）；不新增 echo 事件类型 |
| 落地形态 | **①** 锁文档 + 配置骨架 → **②** companion 接线 Docker + 硬失败 → **③** 单测（无真 Docker）与 README |
| 测试 | **不连**真实 Docker / Redis / MySQL / Milvus / Nacos；用 mock 或配置语义单测 |

## 配置前缀（拟，① 落地时写入白名单）

- `agentscope.runtime.sandbox.enabled`（默认 `false`）
- `agentscope.runtime.sandbox.image`（可空则用文档约定默认 tag）
- 其它非密钥旋钮（若框架 `DockerFilesystemSpec` 需要）同前缀；密钥/ socket 仅环境变量

## 明确不做（本专题）

1. 不默认打开 Docker；不静默把失败降级为 Remote  
2. 不开放 `shell` 工具（即使进了容器文件系统）  
3. 主对话 / echo 不接 DockerFilesystem  
4. 不新增沙箱专用快照体积上限；不改快照到 OSS/Noop  
5. 不做私有镜像仓库认证/拉取编排  
6. 不新增 sandbox.* echo 事件流  
7. 不开 Skills manage / Curator；不借本专题上多 Channel / Wakeup / `mode=mq`  
8. 不改 Memory / Compaction / Eviction / Skills / Permission Gate / Subagent / MCP 已锁约定  

## 落地顺序（一次一块）

再说「继续 / 落地」时按序做：

1. **锁定文 + 配置骨架** — 已完成  
   本文；`AgentRuntimeProperties` 嵌套 `sandbox`（`enabled` 默认 false、`image` 默空、`DEFAULT_IMAGE=ubuntu:24.04`）；  
   `nacos-allowlist.yml` 前缀 `agentscope.runtime.sandbox`；本地 / deploy Nacos 样例默认关；  
   单测只验缺省与白名单，不接线 Docker。  
2. **companion 接线 + 硬失败** — 已完成  
   `CompanionSandboxFilesystem`：`enabled=true` → `DockerFilesystemSpec`（USER + image/默认 tag）；  
   缺 DistributedStore 或 `DockerCliProbe`（`docker version`）失败 → `IllegalStateException`，不回退 Remote；  
   filesystem 从 `configureCommon` 挪到 companion/echo（Remote 与 Docker 规格互斥）；  
   echo 仍 Remote；common 仍 `disableShellTool`；快照/守卫由 Harness 从 DistributedStore 注入。  
3. **单测 + README / 专题收口** — 已完成  
   `agent-runtime` / `agent-bootstrap` / 根 README、`docs/release-gate.md` 与本文一致：  
   默认关；仅 companion 可开 Docker；硬失败无 Remote 回退；shell 仍关；不宣称已接多 Channel / mq / Wakeup。

## 验收要点

- ①：锁文档与白名单/属性骨架一致；默认关；密钥不进 Nacos 明文  
- ②：仅 companion 可进 Docker；硬失败无静默回退；shell 仍关；快照复用 JDBC  
- ③：文档与代码一致；测试不连真 Docker / 中间件  

## 状态

grill 已结束；落地 ①②③ 已完成。本专题收口。若再改规范，先改本文再动代码。

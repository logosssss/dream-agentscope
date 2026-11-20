# MCP 落地（已锁定）

日期：2026-11-12。  
本文只记 grill 收口结论与落地顺序。不改架构指南原文。  
背景：Memory / Compaction / Eviction / Skills（窄）/ Permission Gate / Subagent（窄）已收口；优化主轴仍为 **框架原生对齐（学习向）**。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 优化主轴 | **A**：继续框架原生对齐；不上线压力；多 Channel / mq / Wakeup 仍后置 |
| 原生面 | **MCP**：Harness `ToolsConfig.mcpServers` + 可选注册监听 |
| 默认姿态 | **无清单不连**；有 server 配置才装配 |
| 配置源 | **Nacos 业务配置**可放服务器清单（改旧「Nacos 不做 MCP」）；**不做 MCP 服务发现**；密钥/Token **不进 Nacos 明文**，用环境变量占位替换 |
| 本地/环境变量 | 优先级仍 **环境变量 > Nacos > 本地 yml**；本地可留空结构 |
| 装配范围 | **仅 companion**；echo 不连 |
| 连不上 | 每项可标 **`required`**；未标默认 **软失败**（打日志、跳过该 server、对话继续）；`required=true` 失败则拒绝启动/重建 |
| 热更 | 清单在业务白名单内变更 → 现有 **`rebuildAll()`** 排空重建 |
| 权限 | **沿用 BYPASS**；需拦截的用现有 `permission.rules` / 条件闸 |
| 传输 | **stdio / SSE / streamable HTTP** 三种都支持（映射框架 `McpServerConfig.transport`） |
| 落地形态 | **B**：直接改主路径（白名单 + 配置绑定 + companion `toolsConfig`）；**不做**可丢弃 spike 块 |
| 框架入口 | companion 构建时设 `ToolsConfig.setMcpServers(...)`；需要时挂 `mcpServerRegistrationListener` 记结果 |

## 规则改写要点（相对旧约定）

旧：Nacos「不做服务发现和 MCP」。  
新：**不做服务发现**；**允许**用户进程 Nacos **业务**配置携带 MCP 服务器清单（白名单前缀）；**不做**把 MCP 当注册中心/服务发现；管理进程仍不装配 MCP。

白名单前缀（已写入 `nacos-allowlist.yml`）：

- `agentscope.runtime.mcp.servers`（map：name → transport / command|url / args / env 占位 / required / enableTools / disableTools 等）

## 明确不做（本专题）

1. 不做 MCP **服务发现**（不靠 Nacos 注册 MCP 实例）  
2. echo 不装配 MCP  
3. 不另建 MCP 默认 DENY / 全员 ASK 策略  
4. 不开 Skills manage / Curator；不借本专题上多 Channel / Wakeup / `mode=mq`  
5. 不把 MCP 密钥明文写入 Nacos / 仓库  
6. 不改 Memory / Compaction / Eviction / Skills / Permission Gate / Subagent 已锁约定  

## 落地顺序（一次一块）

再说「继续 / 落地」时按序做：

1. **锁定文 + 规则改写** — 已完成  
   本文；`.cursor/rules/continue-landing.mdc`、`agent-bootstrap` / `agent-admin` / 根 README、`docs/release-gate.md`：  
   「不做服务发现和 MCP」改为「不做服务发现；MCP 清单可进用户进程业务配置、不做 MCP 服务发现」。  
2. **白名单 + 配置绑定 + companion 接线** — 已完成  
   `nacos-allowlist.yml` 前缀 `agentscope.runtime.mcp.servers`；`AgentRuntimeProperties.mcp.servers` 绑定；  
   `McpToolsConfigFactory` 转 `ToolsConfig`（空 map 返回 null、不设 toolsConfig）；  
   companion `customizeCompanion` 装配 + 注册监听；echo 不挂；`${ENV}` 占位展开；  
   `required` 硬/软失败由框架 Registrar 承担（单测用非法 transport 验证）。  
3. **README / 专题收口** — 已完成  
   `agent-runtime` / `agent-bootstrap` / 根 README、`docs/release-gate.md` 与本文一致：  
   companion 已接线、空清单不连、不做服务发现；不宣称已接多 Channel / mq / Wakeup。

## 验收要点

- ①：规则与锁定文一致；旧「不做 MCP」不再挡住业务清单  
- ②：空清单不连；有清单 companion 可装配；echo 无；热更走 rebuild；required 语义可测（可用假配置，不连真外网也可）  
- ③：文档与代码一致；密钥不进 Nacos 明文  

## 状态

grill 已结束；落地 ①②③ 已完成。本专题收口。若再改规范，先改本文再动代码。

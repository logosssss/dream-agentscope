# 图册（对内）

架构图 / 流程图 / 链路图的 **Mermaid 真相源**。业务结论仍以现码、根 [`README.md`](../../README.md)、各 [`docs/*-落地.md`](../) 为准；本目录只做可视化。

专题锁定见 [`docs/diagrams-落地.md`](../diagrams-落地.md)。对外发文配图仍在 [`docs/tutorial/`](../tutorial/)，与本目录隔离。

## 文件分工

| 文件 | 内容 | 图号 |
| --- | --- | --- |
| [architecture.md](./architecture.md) | 部署拓扑、模块依赖、管理进程边界 | 1～3 |
| [flows.md](./flows.md) | 登录准入、WEB/API 主路径、落库、ASK 续跑、管理面 | 4～8 |
| [chains.md](./chains.md) | Middleware、Gateway、Memory、CompactionHook、知识检索 | 9～13 |
| [extensions.md](./extensions.md) | MCP / Sandbox / Wakeup / Skills·Curator / Subagent（默认关或学习面） | 14～18 |

## 目录（1～18）

### architecture.md

| # | 图 | 状态 |
| --- | --- | --- |
| 1 | 部署拓扑（双进程 + 中间件 + 双前端） | 已完成 |
| 2 | Maven/模块依赖方向（含 ArchUnit 禁向） | 已完成 |
| 3 | 管理进程边界（不依赖 runtime/access/Harness） | 已完成 |

### flows.md

| # | 图 | 状态 |
| --- | --- | --- |
| 4 | 登录 → Bearer → 对话准入 | 已完成 |
| 5 | WEB vs API 对话主路径（额度/占锁 503） | 已完成 |
| 6 | 成功轮次落库（session/message/run/step） | 已完成 |
| 7 | ASK 续跑（PendingAsk → decision → 重发） | 已完成 |
| 8 | 管理端只读/配置面（相对用户进程） | 已完成 |

### chains.md

| # | 图 | 状态 |
| --- | --- | --- |
| 9 | companion 一轮 Middleware 顺序 | 已完成 |
| 10 | Channel Gateway + SessionTurnGate / room 归一化 | 已完成 |
| 11 | Memory：`MEMORY.md` flush 与 CRITICAL 不入队 | 已完成 |
| 12 | CompactionHook 口径 C（不装紧急 hook） | 已完成 |
| 13 | 知识检索（切块 → 向量/全文 → Hybrid → Rerank） | 已完成 |

### extensions.md

| # | 图 | 状态 |
| --- | --- | --- |
| 14 | MCP 装配（默认：空清单不连） | 已完成 |
| 15 | Sandbox（默认关；开则 Docker 硬失败边界） | 已完成 |
| 16 | Wakeup（默认关；内部 HTTP + Token） | 已完成 |
| 17 | Skills / Curator（动态关；manage/Curator 不开） | 已完成 |
| 18 | Subagent（本地 probe；Remote 仅学习声明面） | 已完成 |

## 维护

- 行为变更先改代码与对应 `*-落地.md`，再改本图册。  
- 不在本目录宣称 `mode=mq`、产品 Remote 工人、IM 消息通道、API Key 等未交付能力已上线。  

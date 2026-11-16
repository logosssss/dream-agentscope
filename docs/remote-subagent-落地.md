# Remote Subagent 落地（已锁定 · 学习窄专题）

日期：2026-11-16。  
本文只记 grill 收口结论与落地顺序。不改架构指南原文。  
背景：本地 Subagent 已收口（见 `docs/subagent-落地.md`）；API Channel 已收口；总序下一块为 Remote。  
本专题是 **学习窄专题（L）**，不承诺线上远程工人。优化主轴仍为 **框架原生对齐（学习向）**。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 目标 | **L**：学习窄专题；spike + 锁文；**非**产品远程工人 |
| 交付 | **S2**：声明面 spike + 本文 + README 口径（明确不做产品 Remote） |
| spike 深度 | **D1**：只钉声明面；`url` → `isRemote()==true`；读回 url / streaming / AskPolicy；**不**对假 url 发 HTTP、不测端到端 spawn |
| AskPolicy | **A1**：spike 对 `DENY` / `PROPAGATE` 都构造读回；文档写清语义；**产品默认未定**、本专题不选型 |
| 文档/切片 | **C1**：另开本文；`subagent-落地.md` 互指；① spike → ② 文档/README；**不改工厂** |
| 框架面 | `SubagentDeclaration.url(...)`、`remoteStreaming`、`remoteAskPolicy`；Builder 默认 AskPolicy 为 `DENY`（即使无 url，字段仍带默认值；`isRemote` 仍由 url 判定） |
| 本地对照 | 现有 companion `subagent-probe` 保持不变；spike 内另用占位名（如 `remote-probe`） |

## 明确不做（本专题 · X1–X5）

1. **X1** 不改 `HarnessAgentFactory` 主路径（不挂 Remote 声明、不加 enabled 旋钮）  
2. **X2** 不起真实/本机假远端 HTTP；不测端到端 spawn / 流式远程协议  
3. **X3** 不定产品默认 `RemoteAskPolicy`；不做鉴权头 / 密钥进 Nacos 的产品方案  
4. **X4** 不恢复动态 Subagent；不借本片开 `mode=mq` / Inbox / 多角色产品工人  
5. **X5** 不替换或删除现有 `subagent-probe`  

## 落地顺序（一次一块）

再说「继续 / 落地」时按序做：

1. **声明面 spike** — 已完成  
   `RemoteSubagentSurfaceSpikeTest`（`agent-bootstrap`）：  
   - 无 url 本地声明：`isRemote=false`；`getUrl` 空；AskPolicy 字段为 Builder 默认 `DENY`。  
   - 设占位 url：`isRemote=true`；url / `remoteStreaming` / `RemoteAskPolicy`（DENY 与 PROPAGATE）可读回。  
   - 关动态 + 静态挂 Remote 声明：`AgentManager` 可召回且仍标 Remote；**不发起 HTTP**。  
   - 不改 `HarnessAgentFactory`。  
2. **文档 / README 收口** — 已完成  
   本文；`subagent-落地.md` 互指；`agent-runtime` / `agent-bootstrap` README 与 handbook 口径一致：  
   学习声明面已钉；**不宣称**已接产品 Remote / 工厂挂载 / 端到端远程工人。

## 验收要点

- ①：声明面与本地探针对照有结论；不对假 url 发网  
- ②：文档与代码一致；工厂主路径仍仅本地 `subagent-probe`；不宣称产品 Remote  

## 状态

grill 已结束；落地 ①～② 已完成。本学习专题收口。  
产品级 Remote（鉴权、超时、失败语义、工厂旋钮）须另起 grill，不在「继续」里直接升档。若再改本专题规范，先改本文再动代码。

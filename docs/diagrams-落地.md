# 图册整理落地（已锁定）

日期：2026-11-17。  
本文只记 grill 收口结论与落地顺序。不改架构指南原文。  
背景：仓库文档已较齐；根 README 仅有部署拓扑 + 简化对话时序；handbook 多为索引、少嵌图；落地文零星 mermaid。  
本专题建 **`docs/diagrams/` 图册**，补齐架构图 / 流程图 / 链路图（含扩展面），服务对内接手，不升产品能力。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 落点 | **B**：独立 `docs/diagrams/`；handbook / 根 README **互指**，不把图散进十几篇落地文 |
| 覆盖 | **S3**：核心 + Channel/ASK + 上下文治理 + 扩展面（默认关）+ 管理边界；目录见下 1～18 |
| 节奏 | **P2**：图本体在落地 **② 一次画全**；整体仍分三块（见落地顺序） |
| 文件 | **F1**：`README.md` 索引 + `architecture.md` / `flows.md` / `chains.md` / `extensions.md` |
| 与根 README | **R1**：图册为 **图真相源**；根「架构鸟瞰」改为短链 + 要点，避免双份漂移 |
| 载体 | **M1**：仅 Mermaid；不产 PNG、不接 tutorial `make_figs.py` |
| 结论权威 | 图只可视化；业务结论仍听现码 / 根 README / `docs/*-落地.md` |

## 图目录（1～18）

**architecture.md**

1. 部署拓扑（双进程 + 中间件 + 双前端）  
2. Maven/模块依赖方向（含 ArchUnit 禁向）  
3. 管理进程边界（不依赖 runtime/access/Harness）

**flows.md**

4. 登录 → Bearer → 对话准入  
5. WEB vs API 对话主路径（含额度/占锁 503）  
6. 成功轮次落库（session/message/run/step）  
7. ASK 续跑（PendingAsk → decision → 重发）  
8. 管理端只读/配置面（相对用户进程）

**chains.md**

9. companion 一轮 Middleware 顺序（Safety → Eviction/Compaction 危机闸 → 人设 → Memory flush…）  
10. Channel Gateway + SessionTurnGate / room 归一化  
11. Memory：`MEMORY.md` flush 与 CRITICAL 不入队  
12. CompactionHook 口径 C（不装紧急 hook；对照说明）  
13. 知识检索链路（切块 → 向量/全文 → Hybrid → Rerank）

**extensions.md**（须标「默认关」或「学习面」）

14. MCP 装配（清单 → companion ToolsConfig）  
15. Sandbox（Remote → Docker 硬失败边界）  
16. Wakeup（内部 HTTP + Token + Bus）  
17. Skills / Curator（动态关、manage/Curator 不开）  
18. Subagent（本地 probe vs Remote 学习声明面）

## 明确不做（本专题 · X1–X5）

1. **X1** 不改架构指南长文；不把图册写成第二套业务结论  
2. **X2** 不产 PNG / 不接 tutorial `make_figs.py`；不改对外发文母版  
3. **X3** 不画 `mode=mq` 真消费、产品 Remote 工人、IM 消息通道、API Key 等未交付能力的「已上线」图  
4. **X4** 不改生产代码 / 不借本片开 MCP·Sandbox 等产品开关  
5. **X5** 不在各 `*-落地.md` 正文再嵌一份全图（只允许图册 ↔ handbook/根 README 互指）

## 落地顺序（一次一块）

再说「继续 / 落地」时按序做：

1. **锁定文 + 索引壳** — 已完成  
   本文；`docs/diagrams/README.md` 与四文件占位列出 1～18。不改根 README / handbook 入口链。  
2. **交齐图（P2）** — 已完成  
   写满 `architecture.md` / `flows.md` / `chains.md` / `extensions.md` 全部 mermaid（1～18）。结论与现码/落地文对齐。  
3. **入口改链（R1）** — 已完成  
   根 README「架构鸟瞰」改短链到图册；handbook ①③④⑤ 互指；评估文建议下一步已标完成。

## 验收要点

- ①：锁文与 grill 一致；索引壳存在；无入口断链、无全图  
- ②：1～18 均有 mermaid；扩展面标明默认关/学习面；无 PNG、无生产代码变更  
- ③：根 README 不再双写全图；handbook 能链到对应图；不宣称未交付能力已上线  

## 状态

grill 已结束；落地 **①～③ 已完成**。本专题收口。若再改规范，先改本文再动代码/图。

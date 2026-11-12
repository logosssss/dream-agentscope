# Skills 落地（已锁定 · 窄范围）

日期：2026-11-12。  
本文只记 grill 收口结论与落地顺序。不改架构指南原文。  
背景：上下文三件套（Memory / Compaction / Eviction）已收口；优化主轴仍为 **框架原生对齐（学习向）**。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 优化主轴 | **A**：继续框架原生对齐；不上线压力；MCP / 多 Channel / mq 仍后置 |
| 原生面 | **Skills**（窄）：工作区默认 skill + 加载工具面；**关动态 skill** |
| 动态 skill | **`disableDynamicSkills()`** 放 `configureCommon`（companion + echo）。框架默认是开的，必须显式关 |
| 工作区默认 skill | **保持开启**（不调 `disableDefaultWorkspaceSkills`）；目录可空 |
| 种子文件 | **不写**仓库/运行时种子 `AGENTS.md` 或示范 skill；路径约定写文档，文件由运维/部署另放 |
| SkillManage / Curator / Marketplace / Promotion | **本专题不开** |
| 危机闸 | **不做** Skills 专用危机闸（加载手册非 flush/compact 写放大路径） |
| 隔离 | 沿用已锁 **USER** 工作区；skill 目录随用户工作区 |
| 落地形态 | ① spike → ② `disableDynamicSkills` 接线 → ③ README/专题收口 |

## 明确不做（本专题）

1. 不启用 SkillManage / ProposeSkill / Curator / Promotion / Marketplace  
2. 不在仓库或启动时写入 `AGENTS.md` / skill 种子文件  
3. 不上 Subagent / MCP / 多 Channel / `mode=mq`  
4. 不切 Permission 终态 Gate；不补紧急 `compactionHook`  
5. 不把 skill 旋钮放进 Nacos 热更白名单  
6. 不改 Memory / Compaction / Eviction 已锁危机与上下文约定  

## 落地顺序（一次一块）

再说「继续 / 落地」时按序做：

1. **对照 spike** — 已完成  
   `SkillsSurfaceSpikeTest`：默认挂 `HarnessSkillMiddleware` 且 **未 frozen**（动态开）；有 `load_skill_through_path`；  
   无 Curator / skill_manage；`disableDynamicSkills` 后中间件 **frozen**，工作区仓储与 load 工具仍在；  
   `disableDefaultWorkspaceSkills` 会减少仓储（对照，本专题不关）。不改工厂主路径。  
2. **disableDynamicSkills 接线** — 已完成  
   `configureCommon`：`builder.disableDynamicSkills()`；注释写清工作区默认 skill 仍开、种子不写盘。  
   测试：`DisableDynamicSkillsWiringTest`（源码约定 + frozen / 仓储 / load 工具行为）。  
3. **README / 专题收尾** — 已完成  
   `agent-runtime/README.md` 与 `HarnessAgentFactory` 注释写清：动态 skill 已关、工作区默认仍开、无种子；  
   SkillManage / Curator / Marketplace / Promotion 标另块，本专题不开。

## 验收要点

- ①：默认动态开、工作区 skill 面与工具有对照结论  
- ②：common 已 `disableDynamicSkills`；未 disable 默认工作区 skill；无种子写盘  
- ③：文档与工厂注释一致；不宣称已接 manage/curator

## 状态

grill 已结束；落地 ①～③ 已完成。本专题收口。若再改规范，先改本文再动代码。

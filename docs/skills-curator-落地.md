# Skills Curator / manage 落地（已锁定 · 窄范围）

日期：2026-11-13。  
本文只记 grill 收口结论与落地顺序。不改架构指南原文。  
背景：Skills（窄）已收口（`disableDynamicSkills`、工作区默认 skill、无种子）；总序约定 **1 Curator/manage → 2 多 Channel → 3 Remote Subagent**；本专题只做 **1**。优化主轴仍为 **框架原生对齐（学习向）**。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 优化主轴 | **A**：继续框架原生对齐；不上线压力；多 Channel / Remote Subagent / `mode=mq` 本专题后置 |
| 原生面 | **SkillManage / ProposeSkill / Curator**（窄）；**不做** Marketplace / Promotion |
| 默认态 | **默认关**；本专题**不接线显式开启**路径（如何打开标另块） |
| 动态 skill | **主路径继续 `disableDynamicSkills()`**；不恢复动态。spike **双面对照**（框架默认动态开 vs 当前工程动态关） |
| 挂点 | **`configureCommon`**（companion + echo）保证默认关；`customizeCompanion` / `customizeEcho` **不**调用 manage/Curator 开启 API |
| 配置面 | **不进** Nacos 热更白名单；**不增**本地 yml 业务开关；关断只靠工厂代码约定 |
| 危机闸 / Permission | **不做** Curator/manage 专用危机闸；权限沿用现有 Gate；**不改** Permission 终态；**无**新审批流 |
| 文档 | 本文为专题正文；`docs/skills-落地.md` 仅交叉引用 |
| 落地形态 | ① 对照 spike → ② 工厂默认关接线（及注释）→ ③ README / 专题收口 |
| spike 要点 | 框架**默认不开** manage/Curator；无对称 disable API。Curator 装配**嵌在** `enableSkillManageTool` 分支内，单独 `enableSkillCurator` 无效。与 `disableDynamicSkills` **正交** |

## 明确不做（本专题）

1. 不上 Marketplace / Promotion  
2. 不接线「显式开启」manage/Curator（开启路径另块）  
3. 不恢复 `disableDynamicSkills`；不为 Curator 重开动态 skill  
4. 不上多 Channel / Remote Subagent / `mode=mq`（总序 2/3 与 mq 后置）  
5. 不做专用危机闸；不改 Permission 终态；不新增审批流  
6. 不把 Curator/manage 旋钮放进 Nacos 或新增 yml 开关  
7. 不改 Memory / Compaction / Eviction / Skills（窄）/ Subagent / MCP / Sandbox / Wakeup 已锁约定  

## 落地顺序（一次一块）

再说「继续 / 落地」时按序做：

1. **对照 spike** — 已完成  
   `SkillsCuratorSurfaceSpikeTest`：  
   - **动态开 / 动态关**两端默认：无 `SkillCuratorMiddleware`、无 `skill_manage` / `propose_skill`。  
   - **`enableSkillManageTool`**：注册 `skill_manage` + `propose_skill`，挂 `SkillUsageMiddleware`；**不**自动挂 Curator；`runCuratorOnce` 空。动态关下同样可开 manage。  
   - **单独 `enableSkillCurator`**：**不挂**中间件（装配嵌在 manage 开启分支）——关键发现。  
   - **manage + Curator** 同时开：才挂 `SkillCuratorMiddleware`；`SkillCuratorConfig.defaults().enabled()==true`；与 `disableDynamicSkills` 正交。  
   - 无对称 `disableSkillManage` / `disableSkillCurator` API → 「默认关」= **不调用** `enable*`。不改工厂主路径。
2. **工厂默认关接线** — 已完成  
   `configureCommon` / `customizeCompanion` / `customizeEcho`：**不**调用 `enableSkillManageTool` / `enableSkillCurator`（框架已默认关，无需补 disable）。  
   工厂注释写清：单独 Curator 无效、开启须成对、开启路径另块。  
   测试：`SkillsCuratorDefaultOffWiringTest`（源码无 `.enableSkill*(` 调用 + 动态关下无 manage/Curator 面）。
3. **README / 专题收尾** — 已完成  
   `agent-runtime/README.md` 与 `HarnessAgentFactory` 类/方法注释与本文一致；  
   不宣称已接开启路径 / Marketplace / Promotion。

## 验收要点

- ①：动态开/关两面默认关；manage 与 Curator 依赖关系有对照结论（已满足）  
- ②：common/customize 不调 enable*；动态 skill 仍关；无 Nacos/yml 开关（已满足）  
- ③：文档与代码一致；不宣称 Marketplace / Promotion / 产品级开启（已满足）

## 状态

grill 已结束；落地 ①～③ 已完成。本专题收口。  
总序下一块为 **2 多 Channel**（另起 grill / 文档，不混进本文）。若再改本专题规范，先改本文再动代码。

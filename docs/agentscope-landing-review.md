# AgentScope 落地评估（架构 / 工程 · 决策用）

日期：2026-11-16。  
用途：判断是否需要调整**框架用法**与**工程结构**。非传播文。  
不改 [`doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`](../doc/AgentScope%20Java%202.0.4%20产品级架构与工程落地指南%20(2).md) 原文；漂移进对照表。  
代码基线：初评时为 2.0.4；**升版冒烟块后** `pom.xml` → `agentscope.version=2.0.5`（见第六节收口）。

## 评估约定（grill 已锁）

| 项 | 约定 |
| --- | --- |
| 分层 | 框架债 vs 工程债分栏；动作分开 |
| 基准 | 相对本仓目标 × 相对框架能力；冲突进漂移 |
| 动作档 | 保持 / 观察暂不动 / 只改文档 / 工程小改 / 框架用法或版本调整 |
| 覆盖 | 主链路深评 + 扩展面观察 + 指南↔现码主题抽检 |
| 「明确不做」 | 默认记作**克制（优点）**；指南仍承诺、现码不做 → 漂移 |
| 升版 | 轻量扫与痛点相关的上游；无本仓痛点证据不荐为升而升 |
| 偏好 | 契约保守；文档/接线可积极建议改文档 |

---

## 总判（先读这段）

1. **不必做架构级换核或拆掉双进程。** HarnessAgent 每请求构建、`DistributedStore` 走 MySQL、接入层做额度/身份/Channel，与「Spring 工程化 + Harness 执行内核」主轴一致，且已被大量专题与测试钉住。  
2. **工程契约（Redis 额度失败 503、`mode=mq` 启动失败、管理进程不碰 Harness）应保持**；这些与早期指南部分表述冲突时，以**现码 + 根 README + 落地专题**为准，优先**改文档口径/加醒目标注**，不要为对齐旧指南而放宽失败语义。  
3. **框架用法上最大的硬缺口**是 Compaction 紧急 hook：公开 API 无法「只装 hook」，已用 spike 证伪并文档收口（口径 C）。这属于**框架 API 限制**，不是工程偷懒；等上游可拆或接受双挂风险前保持观察。  
4. **Channel 闸键**：已正确发现 `canonicalKey` 含 channel 前缀并落地 `RoomNormalizedSessionTurnGate`——这是吃透框架后的工程补丁，建议保持。  
5. **升 2.0.5**：冒烟块已改 `pom` 并通过仓库根 `mvn test`（JDK 21，498 tests）。产品路径（Channel.send + room 归一化闸）串行仍成立；直调 `gateway.run` 的 spike 对照已改为走 Channel 路径。

---

## 一、六轴总表

图例：**仓+** 相对本仓目标为优点；**仓−** 为债；**框+** 相对框架能力用得好；**框−** 框架侧限制或未吃透；**刻意不做** 默认克制。

| 轴 | 相对本仓目标 | 相对框架能力 | 证据锚点 | 建议动作 |
| --- | --- | --- | --- | --- |
| 隔离与多租户 | **仓+** Bearer 登录会话定身份；租户门；工作区 USER 隔离 | **框+** `RuntimeContext` / 命名空间与产品门对齐 | 根 README；`AccessIdentityFilter`；`HarnessAgentFactory` RemoteFilesystem USER | **保持** |
| 执行生命周期 | **仓+** Channel Gateway + WEB/API 分入口；流式 SSE；ASK 续跑 | **框+** Harness Gateway/ChatUi；**框−** CompactionHook 与危机闸无法干净拆开 | `ChannelGateway`；`docs/permission-gate-落地.md`；`docs/compaction-hook-落地.md` | Hook：**观察**；其余：**保持** |
| 状态与存储边界 | **仓+** Agent 状态 MySQL `DistributedStore`；Redis 只做会话/额度/占锁 | **框+** 按组件装配 State/Base/Snapshot/Guard；Flyway 管 DDL | `DistributedStoreConfig`；`agent-state/README`；`agent-runtime/README` | **保持** |
| 工具 / 权限 | **仓+** 产品条件闸 + 框架 Gate；目录工具；默认可复现的关闭项显式 `disable*` | **框+** PermissionContext BYPASS+规则；**框−** 动态 Skills/Subagent 默认开须显式关（易漏） | `PermissionContextFactory`；`ApprovalMiddleware`；`HarnessAgentFactory` 注释 | **保持**；工厂注释已提醒 → 文档已够 |
| 可观测与成本 | **仓+** 日额度/token/费用/并发；运行与步骤落库；Redis 失败硬失败 | 框架 OTel 可用；计费在产品侧 | `QuotaGuard`；`agent-access/README` 额度段；`docs/release-gate.md` | **保持** |
| 扩展面 | **仓+** API Channel 已收口；MCP/Sandbox/Wakeup/Curator **默认关** | **框+** 真挂第二 Channel；**刻意不做** Remote/mq/MCP 发现 | `docs/api-channel-落地.md` 等 | 扩展面见第三节 **观察** |

---

## 二、主链路（请求阶段）

| 阶段 | 现码做法 | 优劣（双基准） | 建议 |
| --- | --- | --- | --- |
| 进门 | Caller Token + Bearer；不信请求体身份 | **仓+** 防越权；**框+** 与 RuntimeContext 注入分工清晰 | 保持 |
| 闸与额度 | Redis 日限/分钟/占锁/并发；失败 **503 UNAVAILABLE** | **仓+** 不静默放行；相对「Redis 可降级」旧设想是**刻意更严** | 保持契约；指南漂移见第四节 |
| Channel | WEB→ChatUi；API→`ApiChannel`；白名单 `{chatui,api}` | **框+** 多 Channel 真挂；**仓+** room 归一化补框架闸键 | 保持 |
| Agent 轮次 | 工厂 `configureCommon` + companion 定制；Memory 开；Compaction/Eviction/动态 Skill/动态 Subagent 显式关 | **框+** 认清框架默认开项；**框−** 紧急 compaction 只能双挂或整关 | Hook 观察；其余保持 |
| 出站 / HITL | 流式 ASK→`PendingAskStore`；批准后前端重发 | **仓+** 产品语义清晰；**框+** ConfirmResult 续跑 | 保持；2.0.5 HITL 持久化修复可观察 |
| 落库 | 会话元数据、气泡、`agent_run`；写失败不改已生成回复 | **仓+** 审计与体验折中合理 | 保持 |
| 失败语义 | 中间件连不上启动退出；额度 Redis 失败 503；Sandbox 缺依赖硬失败 | **仓+** 可运维；与「降级假活」相对是优点 | 保持 |

---

## 三、扩展面观察表（默认关 / 非主路径）

| 专题 | 本仓状态 | 归类 | 建议 |
| --- | --- | --- | --- |
| API Channel | 已收口 HTTP + 控制台 | 已交付，非观察债 | **保持** |
| MCP | 清单可配；不做服务发现；密钥不进 Nacos | **刻意不做** 发现 | **观察暂不动** |
| Sandbox | 默认关；开则 Docker+Store 硬失败 | **刻意不做** 默认开 | **观察暂不动** |
| Wakeup | 默认关；进程内 Bus；Token 仅环境变量 | **刻意不做** 调度表/Inbox 产品化 | **观察暂不动** |
| Skills / Curator | 动态 skill 关；manage/Curator 默认不 enable | **刻意不做** 产品开启路径 | **观察暂不动** |
| Subagent | 动态关；companion 静态探针 | 产品 Remote 不做；**学习声明面**已钉 | 见 `docs/remote-subagent-落地.md` |
| CompactionHook | 口径 C：不装紧急 hook | **框−** API 限制已文档化 | **观察暂不动**（等上游或新 spike） |
| RocketMQ / `mode=mq` | 无依赖；mq 启动失败 | **刻意不做** | **保持**（不要为对齐指南「接口先行」而引入半套 MQ） |

---

## 四、指南 ↔ 现码漂移（主题抽检）

每行：**指南摘句** ↔ **现码/现专题**。建议默认 **只改文档**（指南正文按仓规不改时：在根 README / 本评估 / 专题交叉引用处醒目标注）。

| # | 主题 | 指南摘句（或专题旧句） | 现码 / 现口径 | 建议 |
| --- | --- | --- | --- | --- |
| D1 | 接入层边界 | 「接入层…**不碰 AgentScope 类型**」 | `agent-access` 的 Gateway/Channel/Wakeup/PendingAsk 大量 `io.agentscope.*`（Channel 适配在接入层是现产品形态） | **已注**（`agent-access/README` gateway 行）；防腐层仍非 P0 |
| D2 | Redis 与主链路 | 「**Redis 故障不影响 Agent 主链路**（额度接口降级，缓存穿透到 MySQL）」 | 额度/占锁 Redis 失败 → **HTTP 503**，不改走内存/MySQL | **已注**（根 README 环境表） |
| D3 | Channel 白名单 | `docs/channel-gateway-落地.md` 仍写 Builder「名单必须**恰好 chatui**」 | 现码白名单 `{chatui, api}`；`docs/api-channel-落地.md` 已收口 | **已注**（channel-gateway 文首修订框） |
| D4 | MQ | 指南：「RocketMQ **接口先行，实现后补**」 | **不引入** RocketMQ；`mode=mq` **启动失败** | **已注**（根 README 深入阅读） |
| D5 | Store 示例混用 | 指南前部出现 `RedisDistributedStore.fromJedis` 示例，后文又定「快照放 MySQL / Redis 不承担 AgentScope 状态」 | 现码 `DistributedStoreConfig` → MySQL 四件套 | **已注**（根 README 深入阅读 + handbook ①） |
| D6 | 工作分支规则 | `.cursor/rules/continue-landing.mdc` 曾写 `feat/agentscope-platform-base` | 继续落地主分支为 `feat/agentscope-channel-gateway` | **已改**（continue-landing 规则） |

---

## 五、相对框架能力的优缺点摘要

### 用得好（框+ / 仓+）

1. **显式对抗框架默认开项**：`disableCompaction` / `disableToolResultEviction` / `disableDynamicSkills` / `disableDynamicSubagents` 写进工厂并注释原因——避免「默认开了却以为没开」。  
2. **Channel 真多通道 + 闸键补丁**：不是只加 HTTP 皮；`RoomNormalizedSessionTurnGate` 针对 `canonicalKey` 行为，属于框架落地正确姿势。  
3. **DistributedStore 与 Flyway 分工**：关自动建表、MySQL 承载状态，符合产品级「状态可迁移」。  
4. **Permission 双闸合并思路清晰**：产品条件更严合并 + 框架 Gate ASK，专题与实现一致。

### 债与限制（框− / 仓−）

1. **Compaction 紧急路径与危机闸互斥于公开 API**（已证伪只装 hook）→ 产品在 CRITICAL 与溢出硬失败之间做了取舍并文档化。  
2. **接入层直接依赖 harness 类型**（相对指南「不碰」）→ 边界清晰度债；功能正确，长期可维护性一般。  
3. **学习/决策文档叠三层**（长指南、落地专题、handbook）→ 工程沟通债；本评估与 handbook 用于减漂移，不能替代改醒目标注。  
4. ~~2.0.4 未吃到上游修复~~ → **已升 2.0.5**（见第六节）。

---

## 六、升版轻量扫（2.0.5）

来源：[agentscope-java v2.0.5 Release](https://github.com/agentscope-ai/agentscope-java/releases/tag/v2.0.5)（2026-11-14，2.0.x 维护版，无删公共 API）。

与本仓相关的修复线索（**未在本仓复现验证**）：

| 上游项 | 与本仓关系 | 动作 |
| --- | --- | --- |
| ConfirmResult / HITL 暂停态持久化、同会话闸与取消写回 | Permission Gate / 流式 ASK | **观察**；升版冒烟时重点测待确认续跑 |
| Compaction 持久化、摘要完整性、pruning | 本仓常规 compaction 关闭 + 危机闸；紧急 hook 仍可能受限 | **观察**；不预期单靠升版解开「只装 hook」 |
| Memory 命名空间读取不再回落到根 `MEMORY.md` | 本仓 USER 隔离 Memory | **观察**；升版需确认工作区路径无旧根文件依赖 |
| Gateway turn lease 按 subscription、空闲闸驱逐 | 与自研 room 归一化并存 | **观察**；升版后回归双 Channel 同 session 串行测试 |
| 默认 Anthropic/Gemini 模型名变更 | 本仓应显式 `modelName`/租户模型配置 | 升版时 **核对配置**，避免默默换模 |
| JDBC/MySQL key 列与 collation 迁移说明 | 使用 MysqlAgentStateStore | 升版前读官方 migration；**勿假设自动改表** |

**结论（冒烟后）：** 已升至 **2.0.5**。仓库根 `mvn test` 全绿。升版中修补：`ChatRunTest` 不再用旧路径 `channel=API`；`ApiChannelSurfaceSpikeTest` 串行用例改为 Channel.send（与接线测试一致）。现网联调（待确认续跑 / Memory 路径）仍建议人工点一次，但不挡本块收口。

---

## 七、动作汇总（给「要不要调」用）

| 动作档 | 项 |
| --- | --- |
| **保持** | 双进程边界；Redis 额度/占锁 503；MySQL DistributedStore；`mode=mq` 拒绝；API Channel 与 room 归一化闸；显式 disable 默认开项；管理进程不依赖 Harness |
| **观察暂不动** | MCP/Sandbox/Wakeup/Curator 产品开启；产品 Remote Subagent（学习面已钉）；CompactionHook 再接线 |
| **只改文档** | D1～D6 **已处理**（醒目标注 / 规则改分支；指南原文仍不改） |
| **工程小改** | 详见 [`docs/engineering-module-review.md`](./engineering-module-review.md)；**不要**为对齐旧指南做 Redis 降级 |
| **框架用法或版本调整** | **2.0.5 已升**（冒烟全绿）。CompactionHook「只装 hook」仍观察 |

---

## 八、建议的下一步（需另说「继续」，且先 grill 的除外）

1. ~~文档漂移清理块~~ — **已完成**（2026-11-16）  
2. ~~2.0.5 升版冒烟块~~ — **已完成**（`agentscope.version=2.0.5`，`mvn test` 498 通过）  
3. **工程 / 模块**：见 [`docs/engineering-module-review.md`](./engineering-module-review.md)（防腐史诗分阶段；每片须再 grill）。  
4. **总序 Remote Subagent**：学习窄专题已收口（`docs/remote-subagent-落地.md`）；产品升档须另 grill。  

## 状态

评估 → 漂移清理 → **2.0.5 升版冒烟**均已收口；工程模块评估另文。架构指南原文未改；防腐代码与 Remote 均须先 grill。

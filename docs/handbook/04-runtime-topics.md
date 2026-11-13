# ④ 运行层与已收口专题（依赖序）

按依赖顺序过完已收口专题。默认关闭能力：**只读**检查点。`packaging` 置顶；`release-gate` 殿后。

---

## 4.0 Packaging（分包）

### 学习目标

知道模块依赖方向与 ArchUnit / 分包约定，避免跨层引用。

### 必读链接（有序）

1. [`docs/packaging-落地.md`](../packaging-落地.md)  
2. [`agent-semantic/README.md`](../../agent-semantic/README.md)、[`agent-runtime/README.md`](../../agent-runtime/README.md) 中与分包相关的说明  

### 要搞清的问题

1. `com.zhu.access` 为什么禁止引用 `..harness..`？  
2. semantic / runtime 按域拆包后，新类应该落在哪一层？  
3. packaging 专题「还没有」里还留着什么？  

### 检查点

只读。

---

## 4.1 Channel Gateway

### 学习目标

弄清 ChatUi 挂载、SessionTurnGate、RuntimeContext 与「不直调 Gateway.run 绕过 Channel」的约束。

### 必读链接（有序）

1. [`docs/channel-gateway-落地.md`](../channel-gateway-落地.md)  
2. `AccessChannelGateway`（`agent-access` … `application.chat.gateway`）  

### 要搞清的问题

1. ChannelManager 白名单今天允许哪些 id？  
2. `MsgContext.canonicalKey` 为何含 channel 前缀？跨 Channel 同 session 为何要 room 归一化闸？  
3. WEB 主路径是否仍只经 ChatUi？  

### 检查点

只读（接线以测试与代码为准；联调主路径已在③完成）。

---

## 4.2 API Channel

### 学习目标

产品级第二入口：`/api/agent/chat*`、额度共桶、控制台页边界。

### 必读链接（有序）

1. [`docs/api-channel-落地.md`](../api-channel-落地.md)  
2. [`agent-access/README.md`](../../agent-access/README.md) 中 API 路径说明  
3. [`web/README.md`](../../web/README.md) — `/api-console`  

### 要搞清的问题

1. 为何不做 API Key / 独立额度桶 `api-chat*`？  
2. 旧 `/api/chat*` 显式 `channel=API` 的行为？  
3. 控制台为何不替代 companion 默认 SSE 路径？  

### 检查点

只读为主；若③已可选打通 `/api-console`，此处复核即可。

---

## 4.3 Permission Engine

### 学习目标

工具权限规则从哪来、引擎如何裁决 Allow / Ask / Deny。

### 必读链接（有序）

1. [`docs/permission-engine-落地.md`](../permission-engine-落地.md)  
2. 运行层 / access 中与 permission rules 相关的 README 段落  

### 要搞清的问题

1. 规则配置优先级（环境 / Nacos / 本地）如何约束热更？  
2. 引擎裁决结果如何进入后续 Gate？  
3. 本专题明确不做哪些放宽？  

### 检查点

只读。

---

## 4.4 Permission Gate

### 学习目标

流式遇 ASK → 待确认 → 用户决定 → 续跑；批准≠自动再跑一轮用户话术。

### 必读链接（有序）

1. [`docs/permission-gate-落地.md`](../permission-gate-落地.md)  
2. [`agent-access/README.md`](../../agent-access/README.md) — approvals / PendingAskStore  

### 要搞清的问题

1. 同步 chat 遇 ASK 是否登记？  
2. 批准后前端为何要「重发上一条」？  
3. `approval_record` / 通知链路「软退役」是什么意思？  

### 检查点

只读。有环境时可在搭子侧栏走一趟待确认（非本段硬门禁）。

---

## 4.5 Memory（长期记忆）

### 学习目标

跨会话权威在工作区 `MEMORY.md`；情景/画像表与主路径关系。

### 必读链接（有序）

1. [`docs/memory-longterm-落地.md`](../memory-longterm-落地.md)  
2. [`agent-access/README.md`](../../agent-access/README.md) 记忆相关段落  
3. [`agent-state/README.md`](../../agent-state/README.md) — 遗留表说明  

### 要搞清的问题

1. 对话主路径是否还写情景/画像表？  
2. 召回时机与条数约束在专题里如何写死？  
3. 危机档对记忆读写有何影响？  

### 检查点

只读。

---

## 4.6 Tool Result Eviction

### 学习目标

工具结果挤出上下文的策略与边界。

### 必读链接（有序）

1. [`docs/tool-result-eviction-落地.md`](../tool-result-eviction-落地.md)  

### 要搞清的问题

1. Eviction 触发条件是什么？  
2. 与 Compaction 的分工？  
3. 明确不做项有哪些？  

### 检查点

只读。

---

## 4.7 Compaction

### 学习目标

上下文压缩的产品口径与默认行为。

### 必读链接（有序）

1. [`docs/compaction-落地.md`](../compaction-落地.md)  

### 要搞清的问题

1. 压缩何时发生？压缩什么？  
2. 失败或跳过时主路径如何表现？  
3. 与 Hook 专题的前置关系？  

### 检查点

只读。

---

## 4.8 CompactionHook

### 学习目标

Hook 接线方式；「只装 hook」类结论以专题为准。

### 必读链接（有序）

1. [`docs/compaction-hook-落地.md`](../compaction-hook-落地.md)  

### 要搞清的问题

1. spike 证伪了什么？最终口径是什么？  
2. Hook 与 Agent 工厂如何接线？  
3. 默认是否开启？  

### 检查点

只读。

---

## 4.9 Skills

### 学习目标

Skills 如何进入 companion / Harness；与工具目录的关系。

### 必读链接（有序）

1. [`docs/skills-落地.md`](../skills-落地.md)  

### 要搞清的问题

1. Skills 默认是否开启？开关在哪？  
2. 与 ProductTool / 目录的边界？  
3. 本专题不做哪些扩展？  

### 检查点

只读。

---

## 4.10 Skills Curator / manage

### 学习目标

Curator 嵌在 manage 开关下；默认关。

### 必读链接（有序）

1. [`docs/skills-curator-落地.md`](../skills-curator-落地.md)  
2. [`docs/skills-落地.md`](../skills-落地.md) 交叉引用  

### 要搞清的问题

1. 为何不能只开 `enableSkillCurator` 而不开 manage？  
2. 默认关时工厂是否调用 `enable*`？  
3. 本专题是否开产品开启路径？  

### 检查点

只读（默认关，不强制打开）。

---

## 4.11 Subagent

### 学习目标

本地 Subagent 边界；与 Remote / mq 的切割。

### 必读链接（有序）

1. [`docs/subagent-落地.md`](../subagent-落地.md)  

### 要搞清的问题

1. 当前已落地的是本地还是 Remote？  
2. 上下文与工具隔离的约束？  
3. Remote Subagent 是否已在本专题交付？  

### 检查点

只读。

---

## 4.12 MCP

### 学习目标

MCP 服务器清单可进业务配置；不做 MCP 服务发现；密钥不进 Nacos 明文。

### 必读链接（有序）

1. [`docs/mcp-落地.md`](../mcp-落地.md)  

### 要搞清的问题

1. 配置前缀是什么？热更白名单如何约束？  
2. 为何不做 MCP 服务发现？  
3. 默认是否启用？  

### 检查点

只读（默认关或不强制连真实 MCP）。

---

## 4.13 Sandbox

### 学习目标

默认关；Docker socket / 凭证不进 Nacos；缺依赖时硬失败语义。

### 必读链接（有序）

1. [`docs/sandbox-落地.md`](../sandbox-落地.md)  

### 要搞清的问题

1. `sandbox.enabled` 默认值？打开后作用范围（如仅 companion）？  
2. 哪些不能进 Nacos？  
3. 打开但缺 Docker/Store 时进程行为？  

### 检查点

只读（不强制开 Docker 沙箱）。

---

## 4.14 Wakeup

### 学习目标

默认关；进程内 Bus；内部 HTTP + `AGENTSCOPE_WAKEUP_TOKEN`；不做调度表消费。

### 必读链接（有序）

1. [`docs/wakeup-落地.md`](../wakeup-落地.md)  
2. [`agent-access/README.md`](../../agent-access/README.md) — WakeupController  

### 要搞清的问题

1. 令牌为何只能环境变量？  
2. 忙会话时 Wakeup 行为？  
3. Inbox / TeamWakeups / 调度表是否在范围内？  

### 检查点

只读（不强制打开）。

---

## 4.15 Release Gate

### 学习目标

开源/发布前自动化与冒烟清单；「还没有」文档化即可不挡门禁的边界。

### 必读链接（有序）

1. [`docs/release-gate.md`](../release-gate.md)  

### 要搞清的问题

1. 自动化门禁最小集合是什么？  
2. 哪些已知缺口允许文档化但不挡发布？  
3. handbook 与 release-gate 职责如何分工？  

### 检查点

只读对照；完整勾选可在准备发版时做，非每日学习硬门禁。

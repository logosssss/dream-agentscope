# API Channel / 多 Channel 落地（已锁定）

日期：2026-11-13。  
本文只记 grill 收口结论与落地顺序。不改架构指南原文。  
背景：Channel Gateway（仅 ChatUi）已收口；总序 **1 Curator/manage（已收口）→ 2 多 Channel（本专题）→ 3 Remote Subagent**。本专题做产品级 **API** 第二入口（含接入 HTTP + 用户端控制台）。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 优化主轴 | 产品级第二入口；学习 Harness 多 Channel；不上线压力；Remote / `mode=mq` 后置 |
| 产品脸 | **`AccessChannel.API`** 程序调用对话（非 IM、非第二条 WEB 页复用旧路径） |
| Harness | **真挂**第二 Channel：`channelId=api`；ChatUi（`chatui`）仍服务 WEB 主路径 |
| ChannelManager | 固定白名单 **`{chatui, api}`**；改写「恰好仅 chatui」约束 |
| SessionTurnGate | **共享**进程内闸；同 session 跨 Channel 串行；不上 Redis 分布式闸。**spike**：`MsgContext.canonicalKey` 含 channel 前缀，裸共享 `LocalSessionTurnGate` **不够**；② 须按 room/session **归一化**闸键 |
| 鉴权 | 与 WEB 同：**Caller Token + Bearer 登录会话**；租户/用户只来自会话；不信请求体身份 |
| HTTP | 独立 **`/api/agent/chat`** 与 **`/api/agent/chat/stream`** |
| 旧路径 | `/api/chat*` 若显式 `channel=API` → **400**，提示改走新前缀；缺省/`WEB` 不变 |
| 额度 | 动作码仍用 **`chat` / `chat-stream`**（与 WEB 共桶） |
| 前端 | 用户端 **web** 新路由页；**复用 `chat-core`**；多 session + 拉历史；只打 API 前缀；不做用量/审批/附件新能力；**不做 web-admin 控制台** |
| 文档 | 本文为专题正文；`docs/channel-gateway-落地.md` 仅交叉引用 |
| 落地形态 | ① spike → ② 最小 ApiChannel 挂 Gateway → ③ `/api/agent/chat*` → ④ web 控制台 → ⑤ README/专题收口 |
| spike 要点 | 白名单 `{chatui, api}` 可行；`canonicalKey=channel\|r:session` 致跨 Channel 默可并行；room 归一化后串行 |

## 明确不做（本专题）

1. 不上 IM / 微信**消息**对话（扫码登录保持原状，不借本专题扩展）  
2. 不上 Remote Subagent、`mode=mq`、RocketMQ 客户端  
3. 不上 Redis / 分布式 SessionTurnGate  
4. 不上服务账号 / API Key（鉴权不放宽为仅 Caller + 请求体身份）  
5. 不开 Skills Curator/manage 产品开启路径；不上第三条及更多 Channel  
6. 不做 web-admin API 控制台；不做独立额度桶 `api-chat*`  
7. 不直调 `Gateway.run` / `runStream` 绕过 Channel（沿用 gateway 专题约束）  
8. 不改 Memory / Compaction / Eviction / Skills / Subagent / MCP / Sandbox / Wakeup / Permission 已锁约定  

## 落地顺序（一次一块）

再说「继续 / 落地」时按序做：

1. **对照 spike** — 已完成  
   `ApiChannelSurfaceSpikeTest`：  
   - 双注册 stub `api` + `chatui`；`requireChatUi` 白名单通过；仅 chatui 仍可；第三者 id 拒绝。  
   - **关键发现**：`MsgContext.canonicalKey` = `channel|r:session`；chatui/api 同 session 直调 `Gateway.run` 时闸键不同 → **可并行**（max≥2）。  
   - **对照**：按 `|r:` room 归一化闸键后同 session **串行**（max=1）。  
   - 主路径 Bootstrap **尚未**挂 api（仍可只注册 chatui）；`API_CHANNEL_ID` 常量与白名单已进 `AccessChannelGateway`。
2. **最小 ApiChannel 挂 Gateway** — 已完成  
   `AccessApiChannel`（`send`/`sendStream`）；`RoomNormalizedSessionTurnGate`；  
   `AccessChannelGateway.startGatewayLocked`：`.channel(ChatUi, Api)` + 归一化闸；  
   WEB 仍只调 ChatUi；`requireApiChannel` / `apiChannel()`。测：`ApiChannelGatewayWiringTest`。
3. **`/api/agent/chat*` 接入** — 已完成  
   `ApiAgentChatController`：`/api/agent/chat`、`/api/agent/chat/stream`；  
   `ApiChatAppService` → `AccessChannelGateway.executeApi` / `streamApi`；额度仍 `chat`/`chat-stream`；  
   旧 `ChatAppService` 显式 `channel=API` → 400。测：`ApiAgentChatPathTest`。
4. **web 控制台页** — 已完成  
   路由 `/api-console` → `ApiConsoleView`；复用 `ChatContainer` + `streamPath=/api/agent/chat/stream`；  
   侧栏只列 `channel=API`；多 session + `/api/sessions*` 历史；不改 companion 主聊天默认路径。
5. **README / 专题收尾** — 已完成  
   `agent-access` / `web` README 与本文一致；不宣称 IM / Remote / mq / API Key。

## 验收要点

- ①：`ChannelManager` 可为 `{chatui, api}`；闸键 channel 前缀与 room 归一化对照（已满足）  
- ②：Gateway 启动挂两条 + room 归一化闸；WEB 主路径仍经 ChatUi（已满足）  
- ③：新 HTTP 需登录；旧路径 `channel=API` → 400；额度动作码未另开桶（已满足）  
- ④：web 新页可多会话联调 API 前缀；不复用为默认 companion 主聊天替代（已满足）  
- ⑤：文档与代码一致；明确不做项未混入（已满足）

## 状态

grill 已结束；落地 ①～⑤ **全部完成**。本专题收口。下一总序块是 **Remote Subagent**（须先 grill，不在本专题「继续」里直接开写）。若再改本专题规范，先改本文再动代码。

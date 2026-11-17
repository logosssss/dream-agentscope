# 流程图（4～8）

结论权威：现码 / [`agent-access/README.md`](../../agent-access/README.md) / [`docs/permission-gate-落地.md`](../permission-gate-落地.md) 等。索引见 [README.md](./README.md)。

## 4. 登录 → Bearer → 对话准入

身份只来自登录会话；请求体/头不能指定用户。Caller 令牌与 Bearer 分工。

```mermaid
sequenceDiagram
  participant C as 客户端
  participant Caller as CallerTokenFilter
  participant Login as 注册/登录 API
  participant Redis as Redis
  participant Ident as AccessIdentityFilter
  participant Chat as 对话 API

  C->>Caller: X-Caller-Token
  Caller->>Login: 通过后 POST /api/login 或 /api/register
  Login->>Redis: 写入 login-session:token
  Login-->>C: Bearer token
  C->>Caller: 后续请求 + Bearer
  Caller->>Ident: Caller 通过
  Ident->>Redis: 解析会话
  Ident->>Chat: 写入租户/用户上下文
  Note over Chat: 无 Bearer → 401；Redis 失败 → 503
```

## 5. WEB vs API 对话主路径

`/api/chat*` → ChatUi（WEB）；`/api/agent/chat*` → API Channel。额度/占锁 Redis 失败返回 **503**，不回落内存/MySQL。

```mermaid
flowchart TB
  subgraph webPath ["WEB"]
    W1["POST /api/chat 或 /api/chat/stream"]
    W2["ChatAppService"]
    W3["Channel = chatui"]
  end
  subgraph apiPath ["API"]
    A1["POST /api/agent/chat*"]
    A2["ApiChatAppService"]
    A3["Channel = api"]
  end
  Gate["额度 / 占锁 / 租户用户 ACTIVE"]
  Gw["ChannelGateway"]
  Agent["HarnessAgent 推理"]

  W1 --> W2 --> Gate
  A1 --> A2 --> Gate
  Gate -->|Redis 失败| S503["HTTP 503"]
  Gate -->|通过| Gw
  W2 --> W3 --> Gw
  A2 --> A3 --> Gw
  Gw --> Agent
```

## 6. 成功轮次落库

写失败不改变已生成回复。MySQL 关闭仅测试场景。

```mermaid
flowchart LR
  Done["对话成功结束"]
  Meta["agent_session_meta\n标题/归属"]
  Msg["chat_message\nuser + assistant"]
  Run["agent_run\n短摘要"]
  Step["agent_run_step\n工具/推理步骤"]
  Cache["会话列表 Redis 缓存\n删失败不挡对话"]

  Done --> Meta
  Done --> Msg
  Done --> Run
  Run --> Step
  Done --> Cache
```

## 7. ASK 续跑

权威在运行层 `PendingAsk*`；旧审批表已硬删。同步 chat 遇 ASK 不登记；流式登记后前端决策续跑。

```mermaid
sequenceDiagram
  participant U as 用户端
  participant Acc as access HTTP
  participant Fac as PendingAskFacade
  participant Store as PendingAskStore
  participant Gw as ChannelGateway
  participant Agent as HarnessAgent

  U->>Acc: 流式对话
  Acc->>Gw: 进入 Channel
  Gw->>Agent: 推理遇 Gate ASK
  Agent-->>Acc: RequireUserConfirm
  Acc->>Store: 登记活单
  Acc-->>U: SSE 待确认
  U->>Acc: GET /api/approvals/pending
  Acc->>Fac: 读活单
  Fac-->>U: 列表
  U->>Acc: POST .../decision
  Acc->>Fac: decideAndResume
  Fac->>Store: 取出
  Fac->>Gw: send Msg + ConfirmResult
  Gw->>Agent: 续跑工具
```

## 8. 管理端相对用户进程

管理面改配置/查审计；不执行对话。用户进程读库内配置与目录。

```mermaid
flowchart LR
  AdminUI["web-admin"]
  AdminP["agent-admin :8100"]
  DB[(MySQL\n租户/额度/工具目录/模型配置等)]
  UserP["agent-bootstrap :8080"]
  Web["web 搭子/控制台"]

  AdminUI --> AdminP
  AdminP --> DB
  Web --> UserP
  UserP --> DB
  AdminP -.->|不调用| UserP
```

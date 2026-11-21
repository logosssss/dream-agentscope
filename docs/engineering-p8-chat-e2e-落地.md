# P8 对话链路端到端

状态：**已完成**（2026-11-21）

## 为什么有这一块

P5 的 E2E 只到「注册 → 登录 → 附件」，**最主的那条路没测**：一轮对话从 HTTP 进来，经过身份过滤器、GateChain（额度与并发）、执行缝，把运行记录和气泡写进 MySQL，再从列表接口读回来。

那条路上每一步都有各自的小单测（`ChatRunTest` 用 mock 的 `AgentExecutor`、`GateChainTest` 用 lambda 闸门），但没有一个用例证明它们**接在一起**时还成立：会话归属闸门之后再记会话、`chat_message` 的 `seq` 从 `max(seq)+1` 起、`agent_session_meta.message_count` 在库里自增而不是读改写、额度只计一次。

## 已锁定

| 项 | 决定 |
| --- | --- |
| 测什么 | `POST /api/chat` 一整轮 + `/api/runs/{id}`、`/api/sessions`、`/api/sessions/{id}/messages`、`/api/quota` 回读 |
| 替身范围 | 只有 `ChannelGateway` 的四个执行方法（`execute` / `executeApi` / `stream` / `streamApi`）；模型层因此完全不被调用 |
| 替身方式 | `@MockitoSpyBean ChannelGateway`，包一层而不换类型：`PendingAskFacadeService`、`WakeupCoordinator` 按具体类型注入的地方照常成立 |
| 容器 | 真 `mysql:8.4` + `redis:7.4-alpine`，库名 `dream_chat_e2e`，与 P5 的库分开 |
| 本地行为 | 没有 Docker 时整类跳过（`disabledWithoutDocker = true`），本地 `mvn test` 仍绿 |
| 不引入 | 不引入 H2、不做假 model 实现、不给模型层加测试钩子 |

## 明确不做

- **X1 不用 H2 或 MySQL 兼容层**。`ON DUPLICATE KEY UPDATE`、`DATETIME(3)`、`utf8mb4_bin` 这些正是要被验证的东西。
- **X2 不做一个「假 ChatModel」**。AgentScope 的模型接口没有为测试留缝，硬做会把运行层绑到测试设施上。
- **X3 不塞进 boundary job**。它要起容器，属于 `verify` 阶段，不是 10 秒护栏。
- **X4 不复用 P5 的容器**。两个类各自起容器更慢但更独立，失败时不会互相污染。
- **X5 不要求人人本地跑**。没有 Docker 就跳过，CI 负责真跑。

## 改动

1. 新增 `agent-bootstrap/src/test/java/com/zhu/bootstrap/MySqlRedisChatE2ETest.java`。
   - 用例 `oneChatTurnPersistsRunMessagesAndSession()`：注册 → `POST /api/chat`（`sessionId=s-chat-1`、`text=你好`）→ 断言回复与 `runId`。
   - 断言 `agent_run` 有一行 `status=SUCCEEDED`（按 `runId + userId + sessionId` 定位），`GET /api/runs/{id}` 也能读到。
   - 断言 `chat_message` 恰好两条、`seq` 升序、角色是 `user` / `assistant`、正文分别是输入与替身回复。
   - 断言 `agent_session_meta` 一行、`message_count=1`、`last_run_id` 已回填。
   - 断言 `GET /api/sessions` 返回该会话（标题取输入首 40 字、`channel=WEB`），`GET /api/sessions/s-chat-1/messages` 返回两条。
   - 断言 `GET /api/quota` 的 `requests.used=1`（注册与登录不计）。
2. `README.md`「测试与门禁」补一条对话 E2E 的说明。

## 验收

| 项 | 结果 |
| --- | --- |
| `mvn -B -ntp -Pstrict -pl agent-bootstrap -am -DskipTests test-compile` | BUILD SUCCESS |
| `mvn -B -ntp -Pstrict verify "-Dtest=!OssObjectStoreTest"` | BUILD SUCCESS；该用例在本机跳过（无 Docker），CI 实跑 |
| `ChatRunTest` / `GateChainTest` / `TenantQuotaTest` 等既有用例 | 未改动，仍全绿 |

## 残留

1. 流式路径（`/api/chat/stream`）没有进这条 E2E：SSE 在 `MockMvc` 下能跑，但断言增量与 `doFinally` 落库要多写不少，留待需要时补。
2. `agent_run_step` 在同步路径本来就不写（只有流式带步骤），所以这里没有断言。
3. 断言用的是 JDBC 直查，没有封装成断言库；后续如果再加两条链路，可以抽 `E2eDatabaseAssertions`。
4. 只覆盖一轮成功；第二轮与失败路径见 P11（`engineering-p11-chat-multiturn-落地.md`）。

## 下一步

- 第二轮与失败路径已由 P11 补上（`engineering-p11-chat-multiturn-落地.md`）。
- 之后候选：流式对话的端到端，或并发同会话的串行验证。

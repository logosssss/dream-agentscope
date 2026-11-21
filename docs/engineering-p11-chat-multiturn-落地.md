# 对话链路：第二轮与失败路径（P11）

状态：**已完成**（2026-11-21）

## 为什么有这一块

P8 的端到端只证明了「一轮成功」：注册 → 发一句话 → 运行记录与两条气泡落库。
但真实使用里更常出问题的是另外两件事：

1. **第二轮**：同一会话再发一句，`seq` 会不会从 3 接着写、会话轮数会不会累加、标题会不会被第二句覆盖、
   `last_run_id` 指向哪一轮。这些只要有一处写错，用户就会看到消息乱序或标题跳动。
2. **执行失败**：模型抛错时，运行记录该是什么状态、气泡要不要写、额度算不算一次。
   如果失败也写气泡，用户会看到一条自己没发过的消息；如果失败不算额度，就能靠失败刷请求数。

单元测试（`ChatRunTest`）已经覆盖了「失败只记异常类型」这类内部约定，但没有一条用例把
「HTTP + 过滤 + 闸门 + 落库」拼起来验证，所以这两件事一直只有读代码的信心。

## 已锁定

| 决定 | 内容 | 理由 |
| --- | --- | --- |
| 复用同一个 E2E 类 | 三个用例都在 `MySqlRedisChatE2ETest`，同一套容器 | 启动容器约 20 秒，拆类等于重复付这笔钱 |
| 第二轮断言 | `chat_message` 四条、`seq` 1..4、`message_count=2`、标题仍是第一轮输入、`last_run_id` 是第二轮 | 这五项任一写错都会在界面上暴露 |
| 失败断言 | `agent_run.status=FAILED`、`error_code=IllegalStateException`、`chat_message` 零行 | 失败不写气泡是硬约定 |
| 失败也算额度 | `/api/quota` 的 `requests.used=1` | 额度在闸门处扣，执行失败不退，避免刷次数 |
| 失败也建会话 | 会话索引有行，`message_count=1` | `record` 在执行前写；「尝试过的轮数」这一口径保持不变（记在残留里） |
| 替身写法 | 一律 `doReturn` / `doThrow` 配 `@MockitoSpyBean` | spy 上写 `when(spy.method())` 会真调用一次原方法，容易误伤 |
| 错误响应断言 | 未分类异常按容器默认 5xx | 接入层只把 `BusinessException` 收成 200/401/503，其余不吞 |

## 明确不做

- **X1 不做流式第二轮**。流式的失败信号是 `SignalType`，断言点在 `doFinally`；补它需要能控制 Flux 的完成方式，
  属于另一个替身维度，等有真实的「中途断开」需求再做。
- **X2 不验证 Redis 中断**。缓存不可用时用户侧返回 503 的口径已有单测，E2E 里停 Redis 会让整个上下文失效，
  修起来比验证的价值大。
- **X3 不改 `message_count` 的语义**。失败也计一轮是现状；改成「成功才算」需要区分 record 与 finish 两个时机，
  会和标题/列表刷新耦合，单独一块处理。
- **X4 不引入假 ChatModel**。仍然替身 `ChannelGateway`，因为要验的是接入层落库，不是模型行为。
- **X5 不要求本地能跑**。三个 E2E 类都 `disabledWithoutDocker = true`，本机跳过、CI 真跑。

## 改动

1. `agent-bootstrap/src/test/java/com/zhu/bootstrap/MySqlRedisChatE2ETest.java`：
   - 抽出 `register(accountName)` 与 `chat(token, sessionId, text)` 两个私有助手；
   - `stubModel()` 改用 `doReturn(...).when(gateway).execute(any())` 形式（spy 安全）；
   - 新增 `secondTurnAppendsMessagesAndKeepsFirstTitle()`：两轮、四条气泡、`seq` 连续、标题不被覆盖、轮数累加、额度=2；
   - 新增 `failedTurnRecordsFailedRunWithoutMessages()`：5xx、`FAILED` + `IllegalStateException`、零气泡、会话行仍建、额度=1；
   - 用例数从 1 增到 3。
2. 文档：`README.md` 的 E2E 段补第二轮与失败；`docs/INDEX.md` 改 P0–P11；`docs/engineering-p8-chat-e2e-落地.md`
   的「残留」与「下一步」指向本文。

## 验收实测

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 编译 | `mvn -B -ntp -Pstrict -DskipTests test-compile` | BUILD SUCCESS |
| 本机全量 | `mvn -B -ntp -Pstrict verify "-Dtest=!OssObjectStoreTest" "-Dsurefire.failIfNoSpecifiedTests=false"` | BUILD SUCCESS；三个 E2E 类在本机跳过 |
| CI | push 后由 GitHub Actions 跑 | 待本次推送后的 run 结果 |

## 残留

1. **失败轮的 `message_count` 仍 +1**（「尝试过的轮数」）。要改成「成功轮数」得把 `record` 的 +1 挪到 `finish`，
   并处理「首轮失败时标题取谁」。
2. **并发同会话没有 E2E**。同会话串行靠 Gateway 排队，E2E 里两个请求并行发起才能验证；
   现在只有单元层面的 `RoomNormalizedSessionTurnGate` 语义。
3. **流式失败路径只有单测**（`ChatRunTest.streamErrorStoresType`），没有走 HTTP 的 SSE 用例。
4. **附件与知识库没有跨轮**。一轮里既发消息又带附件时的顺序未验证。

## 下一步

- 可观测性：把 `request-id` → `agent_run` → `agent_event_audit` 串成可查的一条线。
- 并发同会话的 E2E（两个请求同时打同一 sessionId，断言不交叉、`seq` 不重）。

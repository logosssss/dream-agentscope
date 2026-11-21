# P14 审计归属：`run_id` / `session_id` 写进 `agent_event_audit`

状态：**已完成**（2026-11-22）。范围：用户进程的执行内核与审计写入。管理进程不改。

## 为什么有这一块

`agent_event_audit` 建表时就有 `session_id`、`run_id` 两列（`V2__audit_tables.sql`），但写入器 `MybatisAuditRecorder` 一直只填 `event_id` / `event_type` / `user_id` / `source` / `payload`——这两列从上线起就是空的。

原因在两头：运行标识原本是**执行完成之后**才由框架回复的消息 id 得到（`AgentTurnSupport.runIdOf(Msg)`），而审计中间件是在**执行过程中**写行，那时还没有 id 可写。

于是 P13 之后，用户能凭请求号查到 `agent_run` 那一行，却查不到这一轮对应的审计事件——只能按用户和时间猜。这一块把运行标识提前固定、随上下文传下去，让审计行挂回具体那一轮。

## 已锁定

| 项 | 定法 |
|----|------|
| 运行标识生成时机 | `ChannelGateway` 四个入口（`execute` / `executeApi` / `stream` / `streamApi`）在调用前 `Ids.newId()`，写进 `RuntimeContext` 的 `runId` 键，整轮复用 |
| 与框架消息 id 的关系 | **不再用**框架回复的消息 id 当运行号；`AgentTurnSupport.runIdOf(Msg)` 删除。`AgentResponse.runId` 返回调用前生成的值 |
| 审计端口 | `AuditRecorder` 增加带默认实现的 5 参 `record(action, actorId, sessionId, runId, detail)`；3 参版本保留并委托给 5 参（传 null）。函数式接口性质不变，老调用点不用改 |
| 会话/运行来源 | 中间件从 `CallIdentity` 取；`CallIdentity` 扩成 `(tenantId, userId, sessionId, runId)`，`from(RuntimeContext)` 读 `runId` |
| 空白处理 | 空白一律写 null（`blankToNull`），插入策略会跳过这些列 |
| 任务类审计 | 仍走 3 参：`task.handle` / `task.backfill` / `task.report` / `task.batch` 没有「运行」这个概念 |
| 表结构 | 不动，两列本来就有 |

## 明确不做

- **X1** 不给任务类审计补运行号：调度任务没有 `agent_run`，补一个空壳 id 只会让人误以为能按它查。
- **X2** 不把 `run_id` 塞进 `payload`：列已经存在，塞进 JSON 是同一份信息写两遍。
- **X3** 不改审计表结构、不加索引：现有 `idx_session_created` 够用；按 `run_id` 回查暂时不承诺（见残留）。
- **X4** 不做 trace / OpenTelemetry 串联：这一块只解决「审计行属于哪一轮」，不做瀑布图。
- **X5** 不回填历史行：老行留 `NULL`，能查到的是「当时没写」，不是「写错了」。

## 改动

1. `agent-runtime/src/main/java/com/zhu/runtime/api/event/AuditRecorder.java`：加 5 参默认方法。
2. `agent-runtime/.../infrastructure/persistence/event/MybatisAuditRecorder.java`：3 参转调私有 `write(..., null, null, detail)`；5 参写 `sessionId` / `runId`（空白→null）。
3. `agent-readmodel/.../entity/event/AgentEventAudit.java`：两列注释从「当前不写」改成「执行内核写入时带上，任务类动作留空」。
4. `agent-runtime/.../domain/CallIdentity.java`：扩成 4 字段，`NONE` 补齐，`from(RuntimeContext)` 读 `runId`。
5. `agent-runtime/.../domain/middleware/AuditMiddleware.java`、`FileWriteLimitMiddleware.java`、`application/usage/AuditUsageMeter.java`：改为 5 参，传 `identity.sessionId()` / `identity.runId()`。
6. `agent-runtime/.../application/turn/AgentTurnSupport.java`：`context(request, runId)` 写 `runId`；删除 `runIdOf(Msg)`。
7. `agent-runtime/.../infrastructure/gateway/ChannelGateway.java`：四个入口调用前生成 `runId`；同步返回 `new AgentResponse(runId, text)`；流式去掉 `AtomicReference`，`onStreamEvent` 只记 usage。
8. 测试：`MybatisAuditRecorderTest` 两例（3 参两列留空 / 5 参写入且空白落 null）、`LongTermMemoryIdentityVisibilitySpikeTest` 构造 4 参身份并写 `runId`、`MySqlRedisChatE2ETest` 断言同一 `run_id` 的审计行 > 0 且没有 `agent.call.error`。
9. 审计行的 `user_id` 仍由中间件从身份取；`source` 仍是 `EXECUTOR`。

## 验收（本机实测）

| 项 | 结果 |
|----|------|
| `mvn -B -ntp -Pstrict -DskipTests test-compile` | BUILD SUCCESS（20 个 reactor 项） |
| `MybatisAuditRecorderTest` | 2 例全绿 |
| `AuditUsageMeterTest` + `LongTermMemoryIdentityVisibilitySpikeTest` | 5 例全绿 |
| `mvn -B -ntp -Pstrict verify "-Dtest=!OssObjectStoreTest"` | BUILD SUCCESS；bootstrap 441 例 / 0 失败 / 7 跳过（7 个跳过来自 4 个 Testcontainers 类，本机无 Docker） |
| 端到端审计断言 | `MySqlRedisChatE2ETest` 里 `agent_event_audit WHERE run_id = ? AND session_id = ?` 有行、同 run 无 `agent.call.error`；本机跳过，由 CI 跑 |

## 残留

1. **管理端没有审计查询接口**：行已经写进去了，但界面看不到——README 里的「审计只读」指的是库表，不是页面。
2. `agent_event_audit.run_id` 没有索引：按运行号回查要扫表；当前量小能接受，有查询需求再补。
3. 审计行只在中间件里写：模型执行前就失败（如额度、并发闸门拦下）不会留下 `agent.call` 行，这是有意的——那一轮根本没进入执行。
4. `run_id` 由应用侧生成，与框架内部消息 id 不是同一个值；旧数据里两者无法互查。

## 下一步

- 审计查询接口（按 `run_id` / 用户 / 时间过滤）与管理端详情页串联，把 P13 的请求号、P14 的运行号、`agent_run` 串成一条链。
- 真有按运行号回查的需求时再加 `KEY idx_run (run_id, created_at)`。

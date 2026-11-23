# P13 请求号串联：`X-Request-Id` 落进运行记录

状态：**已完成**（2026-11-21）。范围：用户进程的对话运行记录。管理进程不改。

## 为什么有这一块

用户报「刚才那轮答错了」，运维能看到的是 `agent_run` 里一串时间相近的行、`chat_message` 里一串气泡、`agent_event_audit` 里一串事件——都对不上具体是哪一次点击。

`X-Request-Id` 之前只做幂等键：接了就往 `RequestIdContext` 里放，用完清掉，**不落库、不回显、日志里也没有**。于是它既不能用来查问题，也不出现在任何回执上。

这一块把请求号做成一条可核对的线：前端每条请求生成一个，后端写进 `agent_run.request_id`，并在运行详情里返回；接口再把同一个号回显给调用方，用户可以直接把号贴给运维。

## 已锁定

| 项 | 定法 |
|----|------|
| 请求号来源 | 只认请求头 `X-Request-Id`（`AccessIdentityFilter.REQUEST_ID_HEADER`）；没带就没有 |
| 服务端生成 | **不做**。没带就留空，不凭空造一个号（造出来的号前端不知道，回执也对不上） |
| 落库位置 | `agent_run.request_id VARCHAR(64) COLLATE utf8mb4_bin`，可空，带 `idx_request_id` |
| 长度 | 写入时截到 64（`MysqlRunLog.REQUEST_ID_LIMIT`），空白写 null |
| 流式路径 | 在 `stream()` 入口就把号抄成局部变量；`doFinally` 跑在响应式线程上，读不到过滤器的线程上下文 |
| 回显 | `AccessIdentityFilter` 把同一个号写回响应头；401 / 5xx 也带 |
| 读侧 | `RunSummary.requestId`（列表与详情共用），空给空串 |
| 前端 | `authHeaders()` 每条请求生成一个号；调用方自己传了就不覆盖 |
| 审计 | 不动。`agent_event_audit` 有 `run_id`/`session_id` 列，但写入器仍只填 `event_id`/`event_type`/`user_id`/`source`/`payload` |

## 明确不做

- **X1** 不生成服务端请求号：前端感知不到，回执对不上，等于多一个没人用的列。
- **X2** 不动 `agent_event_audit` 的 `run_id` / `session_id`：那是上一层的关联（审计事件归属哪次运行），需要改动中间件往上下文里传运行标识，单独一块。
- **X3** 不做日志格式改造、不做 trace 采样、不接 OpenTelemetry：请求号先做到「能查」，不是「能看瀑布图」。
- **X4** 不做跨进程传递：管理进程没有对话，不需要它。
- **X5** 不校验请求号格式（不强制 UUID）：调用方可能是脚本、渠道网关，任何非空串都接受，只截长度。

## 改动

1. `agent-db/src/main/resources/db/migration/V46__agent_run_request_id.sql`：给 `agent_run` 加 `request_id` 与 `idx_request_id`。
2. `agent-access/.../persistence/entity/run/AgentRun.java`：加 `requestId` 字段与读写方法。
3. `agent-access/src/main/resources/mapper/AgentRunMapper.xml`：`insertFinished` 写入，`listOwned` / `findOwned` 读回。
4. `RunLog.complete(...)` 增加 `String requestId` 参数；`MysqlRunLog` 截断后写入，`AbsentRunLog` 忽略。
5. `ChatAppService` / `ApiChatAppService`：同步与流式四个入口在方法开头 `RequestIdContext.get()`，私有 `finish(...)` 接收并透传。
6. `RunSummary` 增加 `requestId`，`MysqlRunQuery.summary` 填（null → 空串）。
7. `AccessIdentityFilter`：读到请求号时 `response.setHeader("X-Request-Id", ...)`，401 分支之前就设好。
8. `web/src/shared/api/http.ts`：`newRequestId()`（`crypto.randomUUID()`，没有就时间戳 + 随机串）与 `authHeaders()` 注入；`web/src/shared/types/api.types.ts` 的 `RunSummary` 加 `requestId`。
9. 测试：`ChatRunTest.requestIdIsCopiedIntoRunRecord`（线程上下文 → 运行记录）、`AccessIdentityFilterTest`（有号回显 / 无号静默）、`MySqlRedisChatE2ETest` 失败轮断言 `agent_run.request_id` 与响应头一致；`MigrationCatalogTest` 到 V46。

## 验收（本机实测）

| 项 | 结果 |
|----|------|
| `mvn -B -ntp -Pstrict -DskipTests test-compile` | BUILD SUCCESS |
| `AccessIdentityFilterTest` + `ChatRunTest` | 2 + 7 例全绿 |
| `MigrationCatalogTest` + `SchemaContractTest` | 1 + 2 例全绿 |
| `mvn -B -ntp -Pstrict verify "-Dtest=!OssObjectStoreTest"` | BUILD SUCCESS；bootstrap 440 例 / 0 失败 / 7 跳过（7 个跳过都来自 4 个 Testcontainers 类，本机无 Docker，由 CI 真跑） |
| `web` 的 `pnpm exec vue-tsc -b` / `pnpm lint` | 均 exit 0 |
| `http.test.ts` 的请求号用例 | 本机 vitest 仍 `spawn EPERM`，由 CI 执行 |

## 残留

1. 请求号只在调用方带了才有；前端已默认带，但脚本/渠道网关调用仍可能为空。
2. `agent_event_audit` 的 `run_id` / `session_id` 列此前没写——已由 [P14](./engineering-p14-audit-run-id-落地.md) 补上；管理端仍没有审计查询接口。
3. 页面还不显示请求号作为独立列：`/api/runs/{id}` 已经返回，运行详情页可以加；目前只在**报错文案**里带（见下）。
4. `agent_run.request_id` 没有按请求号回查的接口（只有索引）；运维查库可以，接口没做。

## 补充：报错文案直接带号（2026-11-22）

原来只有接口回显、库里也落了，但用户看不见——报障时还是只能说「刚才那轮」。

现在前端把所有失败文案统一拼上号：`web/src/shared/api/http.ts` 导出 `withRequestId(message, response)`，`apiJson` 的六处抛错（非 JSON、503、非 2xx、空 body、业务失败）与 `useStreaming.startStream` 的三处（503、JSON 错误体、无流）都过一遍；响应头里没有号时原文不变。

于是用户看到的「今日额度已用完（请求号 3f2c…）」可以直接贴给运维，运维按 `agent_run.request_id` 就查到那一轮。

## 下一步

- 运行详情页单独展示请求号（现在只在失败文案里）。
- 把 `run_id` / `session_id` 写进 `agent_event_audit`（需要运行层中间件拿到运行标识）——已由 [P14](./engineering-p14-audit-run-id-落地.md) 完成。

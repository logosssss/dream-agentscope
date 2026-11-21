# 任务消费落地：BACKFILL / REPORT / BATCH 真正干活（P10）

状态：**已完成**（2026-11-21）

## 为什么有这一块

P9 让调度表真的按时触发，但触发出去的任务只有 `DOCUMENT_INDEX` 有人接：
`BACKFILL` / `REPORT` / `BATCH` 走到分发器里「未知类型」那一支，只留一条审计就结束了。
也就是说调度能跑、任务记录是 SUCCEEDED，但**什么也没发生**——这比不触发更容易误导人。

这一块把三个类型补成真实行为，同时把分发器从「硬编码一个索引器」改成「按端口找实现」，
以后加类型不用再改分发器。

## 已锁定

| 决定 | 内容 | 理由 |
| --- | --- | --- |
| 分发方式 | `DispatchingAgentTaskHandler` 注入 `List<AgentTaskExecutor>`，启动时按 `type()` 建表 | 类型→实现是配置事实，不该每次调用现算 |
| 重复类型 | 启动即 `duplicate task executor type: X` | 否则「哪份实现生效」取决于扫描顺序 |
| 空白类型 | 启动即 `task executor type must not be blank` | 空类型永远不会被命中，属于写错了 |
| 未知类型 | 仍只记 `task.handle` 审计 | 兼容旧行为，第三方任务类型可以只登记 |
| 索引执行器 | `DocumentIndexExecutor implements AgentTaskExecutor`，`execute` 委托 `index` | 切块/嵌入/双写只有一份 |
| 回填 | `KnowledgeBackfillExecutor`（`BACKFILL`）复用索引执行器逐篇重跑 | 不复制索引逻辑，失败口径一致 |
| 回填范围 | payload 可选 `kbId` 限定单库，`limit` 限定篇数（默认 20，上限 100） | 库多时不能让一条任务打光嵌入额度 |
| 回填跳过 | `SUCCESS` 与 `PROCESSING` 都跳过 | 处理中再跑会重复写块 |
| 报表 | `UsageReportExecutor`（`REPORT`）只读 `token_usage` / `agent_run` / `chat_message` | 三张表足够回答「用了多少、跑了几次、说了几句」 |
| 报表窗口 | payload `days`，默认 7，上限 90 | 无界窗口会把大表扫穿 |
| 批量清理 | `RetentionBatchExecutor`（`BATCH`）调用 `RetentionSweeper.runOnce()` | 清理口径与定时任务共用，不写第二套 |
| 审计内容 | 只放计数、库数、天数；不放标题、正文、金额以外的东西 | 审计要能公开查，不能把用户内容带进去 |
| payload 解析 | `TaskPayload.text/number/bounded` 统一口径，解析失败给默认值 | payload 是数据不是代码，写错一个数不该让任务死在解析上 |

## 明确不做

- **X1 不给 `BACKFILL` 做「自动发现所有坏文档」的定时任务**。回填是运维动作，由调度表按需登记；
  平台自己定时扫全部用户会把成本变成不可控。
- **X2 不做任务重试队列**。失败就 FAILED，靠人看审计再触发一次；加退避与死信是消息队列的活。
- **X3 不把 `REPORT` 落成一张报表表**。现在只进审计；要做趋势图再单独加表，不为「以后可能」先建。
- **X4 不让 `BATCH` 支持按库/按会话细化**。保留策略本来就是按 `data_type + scope`，细化属于策略表的事。
- **X5 不把执行器注册表暴露成接口**。加类型就加一个 `@Component`，没有运行时注册的入口。

## 改动

1. `agent-runtime/src/main/java/com/zhu/runtime/task/handle/AgentTaskExecutor.java`（新）：`type()` + `execute(AgentTask)`。
2. `agent-runtime/src/main/java/com/zhu/runtime/task/handle/TaskPayload.java`（新）：`text` / `number` / `bounded`。
3. `agent-runtime/src/main/java/com/zhu/runtime/task/handle/DispatchingAgentTaskHandler.java`：改为按类型表分发；构造时校验空白与重复。
4. `agent-runtime/src/main/java/com/zhu/runtime/task/handle/DocumentIndexExecutor.java`：实现端口，`execute` 委托 `index`；payload 取值改用 `TaskPayload`。
5. `agent-access/src/main/java/com/zhu/access/application/task/KnowledgeBackfillExecutor.java`（新）：`BACKFILL`。
6. `agent-access/src/main/java/com/zhu/access/application/task/UsageReportExecutor.java`（新）：`REPORT`。
7. `agent-access/src/main/java/com/zhu/access/application/task/RetentionBatchExecutor.java`（新）：`BATCH`。
8. 测试：`agent-runtime` 的 `DispatchingAgentTaskHandlerTest`（4 例：命中执行、未知只审计、重复类型启动失败、空白类型启动失败）；
   `agent-access` 的 `KnowledgeBackfillExecutorTest`（3 例）、`UsageReportExecutorTest`（2 例）、`RetentionBatchExecutorTest`（2 例）；
   `agent-bootstrap` 的 `DispatchingAgentTaskHandlerTest` 改为按 `execute` 断言（构造时 `type()` 会被读一次，不能断言「零交互」）。
9. 文档：`README.md` 的能力表与「明确不做」、`docs/INDEX.md`、`docs/engineering-p9-schedule-落地.md` 的下一步、
   `agent-access/README.md` 的任务段。

## 验收实测

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 编译 | `mvn -B -ntp -Pstrict -DskipTests test-compile` | BUILD SUCCESS（20 个 reactor 项） |
| 分发器与三个执行器 | `-Dtest=DispatchingAgentTaskHandlerTest,KnowledgeBackfillExecutorTest,UsageReportExecutorTest,RetentionBatchExecutorTest` | runtime 4 例、access 7 例全绿 |
| 全量 | `mvn -B -ntp -Pstrict verify "-Dtest=!OssObjectStoreTest" "-Dsurefire.failIfNoSpecifiedTests=false"` | BUILD SUCCESS（Testcontainers 用例在本机跳过，CI 上真跑） |

## 残留

1. `BACKFILL` 的重试会把文档状态推成 `PROCESSING → SUCCESS/FAILED`，中间进程挂掉仍会留下 `PROCESSING`，
   下次回填会跳过它（`PROCESSING` 是跳过项）。要彻底解决得有超时回收，那是另一块。
2. `REPORT` 只算总量，没有按模型/按天拆分；要趋势图需要新的聚合查询。
3. `BATCH` 的清理范围受限于 `RetentionSweeper` 现有四类（SESSION/MESSAGE/EVENT/ATTACHMENT），
   其它表要清理得先扩 `RetentionTargets`。
4. 三类执行器都没有端到端用例；CI 上的 E2E 目前只覆盖注册登录附件、一轮对话、删除执行、调度触发。

## 下一步

- 对话链路的多轮与失败路径（同一会话连续两轮、模型抛错时的 run/message 状态）。
- 可观测性：把 `task.*` 审计与 run 记录串成一条 trace（现在只有 request-id 透传）。

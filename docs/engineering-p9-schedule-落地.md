# P9 调度真消费

状态：**已完成**（2026-11-21）

## 为什么有这一块

`agent_task_schedule` 从 V28 建表起就是「只登记」：管理端能建、能停、能恢复，但**永远不会有下一次触发**。README 的「明确不做」里写着「调度真正消费」不是缺陷，是当时留给后面的一块。到了 P9，剩下的三个「可登记不执行」里只剩它——删除请求在 P6 已经真删，保留策略在 P6 已经真清理。

真正的缺口不在「少一个定时器」，而在**账算不清**：

- 登记时不算 `next_fire_at`，于是没人知道「这条调度下次什么时候跑」，前端也展示不出来。
- 不校验 cron 语法，写错只有等到真触发那天才暴露（而且永远不触发）。
- 触发之后不写 `last_fire_at` / `last_status`，成功了和失败了看起来一样。
- 表里没有归属用户，而任务管线要求 `userId` 非空（`agent_task.user_id NOT NULL`）——也就是说，**按当时的表结构根本发不出一条合法任务**。

## 已锁定

| 项 | 决定 |
| --- | --- |
| 谁触发 | 用户进程（`agent-access` 的 `TaskScheduleFirer`）：它有任务管线的发布口与 `JdbcTemplate`；管理进程只负责登记、停用、恢复 |
| 存储归属 | `agent_task_schedule` 的 entity / mapper / XML 从 `agent-admin` 挪到 `agent-readmodel`（两个进程都碰同一张表） |
| 下次触发算法 | 只有一份：`com.zhu.ops.schedule.ScheduleTimes`。管理进程登记时算一次，用户进程触发后再算一次 |
| cron 写法 | 交给 Spring 的 `CronExpression`；另外接受五段写法（分 时 日 月 周，秒补 0），六段照旧 |
| 归属字段 | V43 加 `tenant_id` / `user_id` / `agent_id`：没有用户就发不出任务，所以登记时用户必填 |
| 触发结果 | V43 加 `last_status` / `fire_count`；每次触发都写 `last_fire_at` 与 `SUCCEEDED` / `FAILED` |
| 抢占 | `UPDATE ... SET next_fire_at = ? WHERE id = ? AND next_fire_at = ?`：先推进再发布，抢不到就跳过，不会两个进程各发一次 |
| 开关 | `agentscope.schedule.enabled` **默认 false**；关着时进程起来也不会扫表，`runOnce()` 仍可直接调（测试用） |
| 失败处理 | 发布抛错只记 `FAILED`，不重试：下次触发已经推进，避免每轮重试到死 |
| 配置项 | `enabled` / `interval-millis`（默认 30000）/ `initial-delay-millis`（20000）/ `batch-size`（20），四个都进 Nacos 白名单 |

## 明确不做

- **X1 不做 RocketMQ 真发布**。`agentscope.task.mode=mq` 仍然是占位（启动即失败）；这里只保证「到期 → 发布」这一段在同步管线上成立。
- **X2 不做补触发**。恢复一条停用调度只重算下次触发，不补上次漏掉的那次——补触发容易把下游扫崩。
- **X3 不做 payload 编辑器**。payload 列保留，但触发时只带 `{"scheduleId": ...}`：真业务参数留给后面贴具体任务类型时再加。
- **X4 不给管理端加「立即触发一次」**。执行在用户进程，管理端跨进程触发要走第二条通路，收益小于成本。
- **X5 不做多进程选主**。抢占靠 `next_fire_at` 的乐观锁，天然只有一个进程推进成功；没做专门的选主协调。

## 改动

1. `agent-db/src/main/resources/db/migration/V43__task_schedule_firing.sql`：加 `tenant_id` / `user_id` / `agent_id` / `last_status` / `fire_count`。
2. readmodel：新增 `entity/schedule/AgentTaskSchedule`、`persistence/schedule/AgentTaskScheduleMapper`（`recent` / `findByScheduleId` / `disableEnabled` / `restoreDisabledWithNext` / `due` / `advance` / `markFired`）与对应 XML；删除 admin 侧同名三件。
3. `agent-db/src/test/java/com/zhu/db/SchemaContractTest.java`：`READMODEL_OWNED` 加 `agent_task_schedule`（现 16 张表）。
4. `agent-ops` 新增 `schedule/ScheduleTimes`（`nextFireAt` / `nextCron` / `requireValidCron`，五段补秒），配 `ScheduleTimesTest`（5 例）。
5. admin 侧改写：`TaskScheduleSummary` 11 字段、`TaskScheduleCatalog.register` 7 参、`MysqlTaskScheduleCatalog` 登记时算 `next_fire_at`、恢复时重算；`TaskScheduleRequest` 加三字段；`TaskScheduleCatalogTest` 3 例。
6. access 侧新增触发面：`config/ScheduleProperties`、`config/ScheduleConfig`、`application/schedule/ScheduleQueue`（窄端口）、`MysqlScheduleQueue`、`TaskScheduleFirer`；`TaskScheduleFirerTest` 5 例（发布成功、发布抛错记 FAILED、抢不到跳过、空队列、cron 坏记 FAILED）。
7. 配置面：`agent-bootstrap/src/main/resources/nacos-allowlist.yml` 加 4 个 key；`deploy/nacos/agent-bootstrap.yaml` 加 `schedule:` 段（默认关）。
8. 前端 `web-admin`：`TaskScheduleSummary` 类型加归属与触发字段；`schedule-api.ts` 的 register 加 `tenantId` / `userId` / `agentId`；`SchedulesView.vue` 加三个输入与「下次触发 / 上次触发 / 最近状态」三列。
9. E2E：`agent-bootstrap/src/test/java/com/zhu/bootstrap/TaskScheduleFiringE2ETest.java`，两条用例（到期触发一次并落任务与审计、紧接着再跑一次为 0）。

## 验收

| 项 | 结果 |
| --- | --- |
| `mvn -B -ntp -Pstrict test -Dtest=ScheduleTimesTest,TaskScheduleFirerTest,TaskScheduleCatalogTest` | 13 例全绿（5 + 5 + 3） |
| `mvn -B -ntp -Pstrict -DskipTests compile` | BUILD SUCCESS（20 个 reactor 项） |
| `web-admin` 的 `vue-tsc -b` / `pnpm lint` | exit 0 |
| `TaskScheduleFiringE2ETest` | 本机无 Docker 跳过，CI 实跑 |

## 残留

1. **触发出去的任务没有参数**：触发时任务里只有 `scheduleId`。三类任务的最小实现由 P10 补上（见 `engineering-p10-task-handlers-落地.md`），但要按库/按天细化时仍得先让调度表能存 payload。
2. **没有「最后一轮结果」之外的观测**：`last_status` 只有成功/失败，不带原因；要看原因得翻 `agent_event_audit`。
3. **没有租户隔离的强校验**：触发时用行上的 `tenant_id`，但不校验该用户此刻是否仍属于该租户；跨租户迁移的账户可能触发到旧租户名下。
4. **时间都是进程本地时区算的**（`LocalDateTime.now()`），部署在多时区时需要统一到 UTC——P6 的保留策略用的是 UTC，这里不一致。

## 下一步

- 推 CI：本地又累积了 P9 一个提交。
- 三类任务的最小实现已由 P10 补上（`engineering-p10-task-handlers-落地.md`）；下一个候选是把 P6 与 P9 的时间都统一到 UTC，或补对话链路的多轮与失败路径。

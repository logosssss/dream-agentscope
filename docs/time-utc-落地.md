# 时间口径：全仓 UTC 落库

状态：**已完成**（2026-11-26）。范围：业务代码的时钟入口、DATETIME 落库口径、cron 的时区解释。
表结构不动；部署侧的连接串与会话时区见「残留」。

## 为什么有这一块

同一张库里曾经三种口径并存：

| 来源 | 例子 | 实际含义 |
| --- | --- | --- |
| 代码写 UTC | `LocalDateTime.now(ZoneOffset.UTC)` 写 `agent_run.started_at` | UTC |
| 代码写进程本地 | `LocalDateTime.now()` 写 `agent_task.started_at`、`agent_task_schedule.next_fire_at` | 服务器本地（东八区 = UTC+8） |
| 列默认值 | `created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)` | 跟**会话**时区走 |

三者在东八区机器上互相差 8 小时。具体的错，不是「看着别扭」，是真的会出事：

1. **费用记 0**：新增的 `model_price` 行由列默认值写成本地时间（比 UTC 大 8 小时），
   而取价按 `LocalDateTime.now(UTC)` 比较 `effective_from <= now`；插入后的 8 小时内这条价永远取不到，
   `MysqlTokenUsageWriter` 拿不到价 → 这一轮的金额算成 0。
2. **保留策略窗口偏**：`RetentionSweeper` 用 UTC 截止时间比列里的本地时间。
3. **用量报表窗口偏**：`UsageReportExecutor` 取「最近 N 天」的边界是 UTC，行里的时间可能是本地。
4. **同一张表内两个时间列不可比**：`agent_run.started_at`（UTC）与 `created_at`（默认值，本地）差 8 小时。

## 已锁定

| 项 | 定法 |
| ---- | ---- |
| 唯一时钟入口 | `com.zhu.common.util.UtcTimes`：`now()` / `nowMillis()` / `instant()`。业务代码不再直接读墙钟 |
| 落库口径 | 所有 DATETIME 一律 UTC（`LocalDateTime` 语义即 UTC） |
| 静态护栏 | `UtcClockGuardTest` 扫 `src/main/java`，出现 `LocalDateTime.now(` / `Instant.now(` / `ZoneId.systemDefault(` 即红；只放行 `UtcTimes` 与 `ScheduleTimes`（后者的进程默认时区兜底只允许出现一次） |
| cron 时区 | `ScheduleTimes.nextFireAt(cron, delay, fromUtc, zone)`：cron 在 `zone` 里算下一次，换算成 UTC 落库 |
| 时区配置 | `agentscope.schedule.zone`（例如 `Asia/Shanghai`）；空白按进程默认。两个进程都加了白名单，模板默认写 `Asia/Shanghai` |
| 延迟任务 | 与时区无关，直接加秒 |
| Redis 日桶 | 不动：`QuotaGuard` 的日/分钟键一直按 UTC 切 |

## 改动

1. `agent-common/.../util/UtcTimes.java`（新）：唯一入口，附「为什么不散着写」的注释。
2. 17 个业务类把 `LocalDateTime.now(…)` / `Instant.now()` 换成 `UtcTimes`（access / admin / runtime / state / docs）。
3. `agent-ops/.../schedule/ScheduleTimes.java`：`nextFireAt` / `nextCron` 收时区；新增 `zone(String)`。
4. `agent-access/.../config/ScheduleProperties.java`：新增 `zone`；`TaskScheduleFirer` 按它解释 cron。
5. `agent-admin/.../schedule/MysqlTaskScheduleCatalog.java`：注入 `agentscope.schedule.zone`，登记与恢复两处都传时区。
6. 两个 `nacos-allowlist.yml` 加 `agentscope.schedule.zone`；`deploy/nacos/agent-bootstrap.yaml` 默认 `Asia/Shanghai`。
7. 测试：`UtcClockGuardTest`（含「探针会被抓到」的验证）、`ScheduleTimesTest` 增加时区换算与默认时区兜底、`NacosAdminAllowlistTest`（顺带给以前无人加载的管理端白名单加一道解析护栏）、调度相关用例改用 UTC 基准。

## 上线前必读（旧数据的过渡）

改造前写入的行，时间列里是**服务器本地时间**（比 UTC 大 8 小时）。改造后按 UTC 解释，于是这些行看起来「更晚」：

| 现象 | 方向 | 处理 |
| --- | --- | --- |
| 已排定的 `agent_task_schedule.next_fire_at` | 会**晚 8 小时**才触发一次 | 要么等它自然过去，要么按部署时区手工平移（见下） |
| 保留策略对旧行 | 更**晚**才删（保留期变长 8 小时） | 偏保守，可不动 |
| 用量/报表对旧行 | 旧行被算得「更新」 | 只影响过渡窗口内的统计 |

平移示例（**先备份、先 SELECT 核对**，`8` 换成部署时区的偏移）：

```sql
-- 1) 先看：把会受影响的排期列出来
SELECT schedule_id, next_fire_at, UTC_TIMESTAMP() AS now_utc
FROM agent_task_schedule
WHERE enabled = 1 AND next_fire_at > UTC_TIMESTAMP();

-- 2) 再改：整体减 8 小时（只在部署时区为 UTC+8 时成立）
UPDATE agent_task_schedule
SET next_fire_at = next_fire_at - INTERVAL 8 HOUR
WHERE enabled = 1 AND next_fire_at > UTC_TIMESTAMP();
```

## 明确不做

- **X1** 不改表结构、不改列默认值：`CURRENT_TIMESTAMP` 仍按会话时区，见残留 1。
- **X2** 不做 Flyway 数据迁移：偏移量取决于部署时区的历史事实，写死在迁移里会让别处部署更错。
- **X3** 不把 Redis 的日桶改成北京时间：额度按 UTC 日切是既有约定（东八区 08:00 重置）。
- **X4** 不在测试里固定时钟：本轮只统一「哪里读钟」，可注入的 `Clock` 该用的地方照旧（`QuotaGuard`、`PermissionConditions`）。

## 残留

1. **列默认值仍跟会话时区**：没有显式写入的时间列（多数 `created_at`）取 `CURRENT_TIMESTAMP`，
   仍由 MySQL 会话时区决定。彻底统一要在连接串上把会话时区钉成 UTC
   （Connector/J 8：`connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`），
   属部署侧改动，本轮未做——它改的是「进程看到的时间」，需要连真库验证，不能靠单测。
2. **管理进程的时区是构造期注入**：改 `agentscope.schedule.zone` 后管理端要重启，用户进程下一次扫描就生效。
3. **调度仍是「先推进再发布」**：与时间口径无关，但排障时要知道失败只留 `last_status`。

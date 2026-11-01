# agent-admin

管理进程。给运维看进程状态，不参与对话。和用户进程分开启动，端口 `8100`。

坐标：`com.zhu:agent-admin`。包名：`com.zhu.admin`。

## 依赖

依赖 `agent-common`、`agent-audit`、Web 和 Redis。用审计模块的查询接口读库。不依赖 `agent-runtime`、`agent-access`，也不依赖 Harness。Redis 只用于启动探测。

`agent-bootstrap` 不依赖本模块。用户进程扫描 `com.zhu` 时，classpath 上没有这些类，管理接口不会混进对话进程。

## 启动

在仓库根、使用 JDK 21：

```text
mvn -pl agent-admin -am spring-boot:run
```

`src/main/resources/application.yml` 把端口固定为 `8100`。本进程排除 Flyway，不改表。MySQL、Redis、Milvus 没配或连不上就退出。连接池最大 20、最小空闲 5，和用户进程加在一起要算进 MySQL `max_connections`。停机时先不再接新 HTTP，最多再等 30 秒。

## 接口

| 路径 | 作用 |
| --- | --- |
| `GET /health/live` | 只表示管理进程还在，不查用户进程、数据库或模型 |
| `GET /health/ready` | 探测 MySQL、Redis、Milvus。任一项失败 HTTP 503。测试里三项为 SKIPPED |
| `GET /api/admin/info` | 角色为 `admin` |
| `GET /api/admin/audits` | 最近审计，默认 50 条，最多 100 条 |
| `GET /api/admin/approvals` | 决定为 `PENDING` 的审批，条数规则同上 |
| `POST /api/admin/approvals/{approvalId}/decision` | 把该单改成 `APPROVED` 或 `REJECTED`。通道固定为 `CONSOLE`。关掉对应待办，仍不执行工具。单据不存在时返回找不到 |
| `POST /api/admin/billing/drafts` | 按租户和区间生成一张草稿账单。MySQL 关闭时返回不可用 |
| `POST /api/admin/billing/confirmations` | 把对应草稿改成已确认，不重算金额。没有账单时返回找不到 |
| `POST /api/admin/billing/invoices` | 把已确认的账单改成已开票，不重算金额。草稿要先确认 |
| `POST /api/admin/billing/payments` | 把已开票的账单改成已付款，不重算金额。还没开票的不能付 |
| `GET /api/admin/tenants` | 最近登记的租户，默认 50 条，最多 100 条。含停用和关闭。MySQL 关闭时返回不可用 |
| `POST /api/admin/tenants` | 登记一个可用租户，套餐固定 `standard`。不建用户。MySQL 关闭时返回不可用 |
| `POST /api/admin/tenants/{tenantId}/suspend` | 把可用租户改成 `SUSPENDED`。不删数据，也不关账号。MySQL 关闭时返回不可用 |
| `POST /api/admin/tenants/{tenantId}/close` | 把尚未关闭的租户改成 `CLOSED`。不删数据。MySQL 关闭时返回不可用 |
| `POST /api/admin/tenants/{tenantId}/restore` | 把停用或关闭的租户改回 `ACTIVE`。不改用户状态。MySQL 关闭时返回不可用 |
| `GET /api/admin/tenant-users` | 某个租户最近登记的用户，默认 50 条，最多 100 条。含停用。必须带租户。MySQL 关闭时返回不可用 |
| `POST /api/admin/tenant-users` | 给已登记租户挂一个可用用户。不写角色，也不登录。MySQL 关闭时返回不可用 |
| `POST /api/admin/tenant-users/{tenantId}/{userId}/disable` | 把可用用户改成 `DISABLED`。不删数据。MySQL 关闭时返回不可用 |
| `POST /api/admin/tenant-users/{tenantId}/{userId}/restore` | 把停用用户改回 `ACTIVE`。不写角色。MySQL 关闭时返回不可用 |
| `POST /api/admin/tenant-quotas` | 登记每日请求上限，类型固定 `REQUESTS`，周期固定 `DAILY`。不改已有行。MySQL 关闭时返回不可用 |
| `POST /api/admin/tenant-quotas/{tenantId}` | 改已有的每日请求上限。没有这一行返回找不到。不重置已用量。MySQL 关闭时返回不可用 |
| `GET /api/admin/quota-usage` | 某个租户最近的每日请求流水，默认 50 条，最多 100 条。不回补。MySQL 关闭时返回不可用 |
| `GET /api/admin/model-prices` | 最近的模型价格，默认 50 条，最多 100 条。MySQL 关闭时返回不可用 |
| `POST /api/admin/model-prices` | 再插入一行价格。不改旧行。取价时用生效开始最晚且仍有效的一行 |
| `POST /api/admin/model-configs` | 登记一条模型配置，版本固定 `1.0.0`。用户进程对话会选用启用行。不解析参数。MySQL 关闭时返回不可用 |
| `POST /api/admin/retention-policies` | 登记一条数据保留策略。不执行清理。MySQL 关闭时返回不可用 |
| `POST /api/admin/task-schedules` | 登记一条任务调度。cron 和延迟二选一。不计算下次触发，也不消费。MySQL 关闭时返回不可用 |
| `GET /api/admin/operation-audits` | 最近的后台操作，默认 50 条，最多 100 条。MySQL 关闭时返回不可用 |
| `GET /api/admin/safety-interventions` | 最近的安全干预，默认 50 条，最多 100 条。不含用户原文。MySQL 关闭时返回不可用 |
| `POST /api/admin/operation-audits` | 登记一条后台操作。不写前后快照，也不改业务结果。其它成功写入也会自动记一行。MySQL 关闭时返回不可用 |
| `POST /api/admin/deletion-requests/{requestId}/decision` | 把待处理的删除请求改成 `APPROVED` 或 `REJECTED`。不执行删除 |
| `POST /api/admin/eval-datasets` | 登记一个评测数据集，版本固定 `1.0.0`。不执行。MySQL 关闭时返回不可用 |
| `POST /api/admin/eval-datasets/{datasetId}/cases` | 给该数据集的 `1.0.0` 追加一条用例。数据集不存在时返回找不到。不执行 |
| `POST /api/admin/eval-runs` | 为该数据集的 `1.0.0` 登记一次执行，记下用例条数。状态停在 `RUNNING`。不调用模型 |
| `POST /api/admin/eval-runs/{evalRunId}/score` | 给仍在跑的执行写入通过数和通过率，改成 `SUCCEEDED`。不调用模型。MySQL 关闭时返回不可用 |

业务接口要带 `X-Caller-Token`，值来自 `AGENTSCOPE_ADMIN_TOKEN`，和用户进程的令牌分开。没配或对不上时 HTTP 401。存活和就绪不校验。这不是用户登录。

成功的写入接口会再记一行 `operation_audit`。操作人来自 `X-Operator-Id`，没有时是 `admin`。查询、手工 `POST /api/admin/operation-audits` 和业务失败不自动记。审计写失败不改变这次接口结果。MySQL 关闭时跳过。

扫描包只有 `com.zhu.admin`。另外显式引入 `AuditQuery`、`ApprovalDecider`、关待办和 MySQL 打开时的 Mapper 配置，不引入 `AuditRecorder`、`ApprovalRecorder`、`SafetyInterventionRecorder`。申请单和干预写入仍在用户进程。用户进程不依赖本模块，管理接口不会混进对话进程。

## 还没有

用户账号登录。本进程不提供聊天接口，也不在批准后执行工具，也不按保留策略清理数据，也不按已登记的调度去触发任务，也不在批准删除后真正删数据。仓库不放模型单价，也不放调用方令牌。

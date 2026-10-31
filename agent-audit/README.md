# agent-audit

审计横切。操作记录和审批落库的门面放在这里。被审计的模块依赖这些接口，本模块不反向依赖它们。

坐标：`com.zhu:agent-audit`。包名：`com.zhu.audit`。

## 依赖

依赖 `agent-common`、Spring 和 `mybatis-plus-spring-boot3-starter`。正式配置打开 MySQL 后，用 `BaseMapper.insert` 写入 `agent_event_audit` 和 `approval_record`。表在 `db/migration`，由用户进程的 Flyway 执行。MyBatis-Plus 不建表。测试关掉开关，不创建 SqlSession。

不能依赖 `agent-runtime`、`agent-access`、`agent-semantic`。运行层需要记审批时调用本模块的接口，或发布本模块定义的事件。

## 包

| 包 | 类型 | 作用 |
| --- | --- | --- |
| `api` | `AuditRecorder` | 记一条操作。`detail` 不放密钥和全文提示词 |
| `api` | `ApprovalRecorder` | 记下一次待审批 |
| `api` | `AuditQuery` | 最近审计、全部待审批，以及按租户和用户查待确认 |
| `api` | `ApprovalDecider` | 把 PENDING 改成 APPROVED 或 REJECTED。不写执行结果 |
| `api` | `BillingRollup` | 按租户和区间把 `token_usage` 收成一张 `DRAFT` |
| `application` | `MysqlReadiness` | 校验数据库连接。关闭时跳过，不跑业务 SQL |
| `domain.event` | `ApprovalRequestedEvent` | ASK 时的跨模块事件。待确认列表不靠订阅它 |
| `infrastructure.persistence` | `MybatisAuditRecorder`、`MybatisApprovalRecorder` | MySQL 打开时的写入。审批新单为 `PENDING`，审计来源为 `EXECUTOR` |
| `infrastructure.persistence` | `AgentEventAuditMapper`、`ApprovalRecordMapper` | 继承 `BaseMapper`。简单插入不写 SQL |
| `resources/mapper` | 同名 XML | `resultMap`、条件查询和决定更新。不把同一条 `INSERT` 再写一遍 |

事件放在发布相关的领域包，不放进 `agent-common`。`agent-common` 不承载业务事件。

`approval_record.tenant_id` 由 `V3__approval_tenant.sql` 补上。没有租户时写成空串。

模型调用的 token 用量也记在这张表里，动作名是 `model.usage`。同时写入 `token_usage`：同步对话的 `run_id` 与 `agent_run` 相同，流式对话的 `agent_run` 由接入层另生成，两边可能不同。计价读 `model_price` 里当前生效的一行，按每千 token 的单价计算，保留 6 位小数。没有命中的价格时金额记 0，币种是 `CNY`。用户或模型为空时不写这张表。写失败不改变回复。

已批准且还没执行的单据，由用户进程在下一次同名工具调用时占上并放行。结束后把 `result` 写成 `{"status":"SUCCEEDED"}`、`FAILED` 或 `CANCELLED`，不存工具输出。管理进程的决定接口仍不执行工具。

待确认事件再写 `approval_notification`。接收人是发起用户，通道是 `WEB`，状态是 `PENDING`。没有接收人时不写。不发送，也不把状态改成已发送。写失败不改变这次询问。管理进程不查询这张表。

同一事件再写 `user_todo`。类型是 `APPROVAL`，状态是 `OPEN`，关联审批单，标题是工具名。同一用户和审批单再开一次不再插一行。`GET /api/todos` 只列出这个用户还开着的审批待办。批准或拒绝还不关待办。确认和任务待办还不写。

`POST /api/feedback` 追加 `user_feedback`。评分是 1 到 5，说明最长 1000 字。不存标签，也不要求这次运行已经在 `agent_run` 里。附件还不收，文件没有对象存储。

`POST /api/admin/eval-datasets` 登记 `eval_dataset`，版本固定 `1.0.0`。同一标识再登记这个版本返回冲突。`POST /api/admin/eval-datasets/{datasetId}/cases` 给这个版本追加 `eval_case`。序号从 1 起，输入和期望输出最长 4000 字。数据集还没登记时拒绝。同一序号再写一次返回冲突。不存标签，也不执行评测。

`POST /api/admin/billing/drafts` 按租户和区间生成 `billing_record`。区间最长 366 天，起点含、终点不含。没有用量也是一张 0 金额的草稿。同一区间再生成一次返回冲突。区间里有两种币种时拒绝。`POST /api/admin/billing/confirmations` 把这张草稿改成 `CONFIRMED`，不重算金额。已经确认过再确认一次返回冲突。`POST /api/admin/billing/invoices` 只把已确认的账单改成 `INVOICED`，草稿不能直接开票。`POST /api/admin/billing/payments` 只把已开票的账单改成 `PAID`，不重算金额。已确认但还没开票的不能直接付款。

`POST /api/admin/model-prices` 再插入一行 `model_price`。不改旧行。生效开始为空时用 UTC 现在。仓库不放单价。

`POST /api/admin/tenants` 登记 `tenant`。状态是 `ACTIVE`，套餐是 `standard`。同一标识再登记一次返回冲突。用户进程在 MySQL 打开时只允许这个状态的租户对话。停用还不在这里改。

`POST /api/admin/tenant-quotas` 登记 `tenant_quota` 的每日请求上限。类型是 `REQUESTS`，周期是 `DAILY`。上限 0 表示不限额。同一租户再登记一次返回冲突，不覆盖旧值。已用量仍在 Redis。按 token、费用或并发的上限还不写。

当日计数通过后追加 `quota_usage_log`。数量是 1，不填 `run_id`，因为运行还没开始。不限额时不写。写失败不改变这次请求。`GET /api/admin/quota-usage` 按租户看最近的流水，默认 50 条，最多 100 条。负数回补还不写。

## 还没有

退款不在这张表上做。付款只改状态。

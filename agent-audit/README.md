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

`POST /api/admin/billing/drafts` 按租户和区间生成 `billing_record`。区间最长 366 天，起点含、终点不含。没有用量也是一张 0 金额的草稿。同一区间再生成一次返回冲突。区间里有两种币种时拒绝。确认和开票还没有。

## 还没有

账单的确认、开票和付款。价格要写入 `model_price` 后才会出现在用量金额里，仓库不放单价。

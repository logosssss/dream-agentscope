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
| `application` | `MysqlReadiness` | 校验数据库连接。关闭时跳过，不跑业务 SQL |
| `domain.event` | `ApprovalRequestedEvent` | ASK 时的跨模块事件。待确认列表不靠订阅它 |
| `infrastructure.persistence` | `MybatisAuditRecorder`、`MybatisApprovalRecorder` | MySQL 打开时的写入。审批新单为 `PENDING`，审计来源为 `EXECUTOR` |
| `infrastructure.persistence` | `AgentEventAuditMapper`、`ApprovalRecordMapper` | 继承 `BaseMapper`。简单插入不写 SQL |
| `resources/mapper` | 同名 XML | `resultMap`、条件查询和决定更新。不把同一条 `INSERT` 再写一遍 |

事件放在发布相关的领域包，不放进 `agent-common`。`agent-common` 不承载业务事件。

`approval_record.tenant_id` 由 `V3__approval_tenant.sql` 补上。没有租户时写成空串。

模型调用的 token 用量也记在这张表里，动作名是 `model.usage`。

## 还没有

批准后的执行时间与结果回写。独立计费表还没有。

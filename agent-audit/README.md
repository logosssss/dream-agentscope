# agent-audit

审计横切。操作记录和审批落库的门面放在这里。被审计的模块依赖这些接口，本模块不反向依赖它们。

坐标：`com.zhu:agent-audit`。包名：`com.zhu.audit`。

## 依赖

依赖 `agent-common`、Spring 和 `mybatis-plus-spring-boot3-starter`。`agentscope.distributed.mysql.enabled=false` 时用进程内实现，也不会创建 SqlSession。打开后用 `BaseMapper.insert` 写入 `agent_event_audit` 和 `approval_record`。表在 `db/migration/V2__audit_tables.sql`，由 Flyway 执行。MyBatis-Plus 不建表。

不能依赖 `agent-runtime`、`agent-access`、`agent-semantic`。运行层需要记审批时调用本模块的接口，或发布本模块定义的事件。

## 包

| 包 | 类型 | 作用 |
| --- | --- | --- |
| `api` | `AuditRecorder` | 记一条操作。`detail` 不放密钥和全文提示词 |
| `api` | `ApprovalRecorder` | 记下一次待审批 |
| `api` | `AuditQuery` | 最近审计和待审批的只读查询。MySQL 关闭时返回空列表 |
| `api` | `ApprovalDecider` | 把 PENDING 改成 APPROVED 或 REJECTED。不写执行结果 |
| `domain.event` | `ApprovalRequestedEvent` | ASK 时的跨模块事件 |
| `application` | `MemoryAuditRecorder`、`MemoryApprovalRecorder` | MySQL 关闭时的进程内实现 |
| `infrastructure.persistence` | `MybatisAuditRecorder`、`MybatisApprovalRecorder` | MySQL 打开时的写入。审批新单为 `PENDING`，审计来源为 `EXECUTOR` |
| `infrastructure.persistence` | `AgentEventAuditMapper`、`ApprovalRecordMapper` | 继承 `BaseMapper`。简单插入不写 SQL |
| `resources/mapper` | 同名 XML | `resultMap`、条件查询和决定更新。不把同一条 `INSERT` 再写一遍 |

事件放在发布相关的领域包，不放进 `agent-common`。`agent-common` 不承载业务事件。

打破循环的方式：运行层的 `ApprovalMiddleware` 发布 `ApprovalRequestedEvent`，接入层订阅后再推送。运行层因此不需要依赖 `agent-access`。

## 还没有

批准后的执行时间与结果回写。MySQL 关闭时写入仍在本进程内存里，重启会丢；查询返回空列表，决定接口返回找不到。

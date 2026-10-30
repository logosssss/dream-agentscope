# agent-audit

审计横切。操作记录和审批落库的门面放在这里。被审计的模块依赖这些接口，本模块不反向依赖它们。

坐标：`com.zhu:agent-audit`。包名：`com.zhu.audit`。

## 依赖

依赖 `agent-common` 和 Spring。实现类是进程内记录，还没有数据库。

不能依赖 `agent-runtime`、`agent-access`、`agent-semantic`。运行层需要记审批时调用本模块的接口，或发布本模块定义的事件。

## 包

| 包 | 类型 | 作用 |
| --- | --- | --- |
| `api` | `AuditRecorder` | 记一条操作。`detail` 不放密钥和全文提示词 |
| `api` | `ApprovalRecorder` | 记下一次待审批 |
| `domain.event` | `ApprovalRequestedEvent` | ASK 时的跨模块事件 |
| `application` | `MemoryAuditRecorder`、`MemoryApprovalRecorder` | 接口的进程内实现。表建好之前先让 Hook 能注入 |

事件放在发布相关的领域包，不放进 `agent-common`。`agent-common` 不承载业务事件。

打破循环的方式：运行层的 `ApprovalMiddleware` 发布 `ApprovalRequestedEvent`，接入层订阅后再推送。运行层因此不需要依赖 `agent-access`。

## 还没有

Mapper、`agent_event_audit` / `approval_record` 表。进程重启后内存里的记录会丢。

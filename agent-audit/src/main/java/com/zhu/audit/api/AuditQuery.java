package com.zhu.audit.api;

import java.util.List;

/**
 * 审计和审批的只读查询。管理进程通过它读已经落库的数据，不读另一份 JVM 的内存。
 */
public interface AuditQuery {

    /**
     * 最近的审计事件，按入库顺序从新到旧。
     *
     * @param limit 条数。小于 1 时按 50，大于 100 时截断到 100
     */
    List<AuditEventRow> recentEvents(int limit);

    /**
     * 仍待处理的审批，按申请顺序从新到旧。
     *
     * @param limit 条数。小于 1 时按 50，大于 100 时截断到 100
     */
    List<ApprovalRow> pendingApprovals(int limit);
}

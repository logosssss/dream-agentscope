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

    /**
     * 这个租户和用户仍待处理的审批。租户为空串表示单据上没有租户。
     *
     * @param limit 条数。小于 1 时按 50，大于 100 时截断到 100
     */
    List<ApprovalRow> pendingFor(String tenantId, String userId, int limit);

    /**
     * 本人本租户的一张审批。含已决定行。找不到或不属于本人时返回 null，不抛异常。
     */
    ApprovalRow ownedOne(String tenantId, String userId, String approvalId);
}

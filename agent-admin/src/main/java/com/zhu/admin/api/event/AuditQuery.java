package com.zhu.admin.api.event;

import java.util.List;

/**
 * 审计只读查询。管理进程通过它读已经落库的事件，不读另一份 JVM 的内存。
 * 旧审批表查询已随硬删移除。
 */
public interface AuditQuery {

    /**
     * 最近的审计事件，按入库顺序从新到旧。
     *
     * @param limit 条数。小于 1 时按 50，大于 100 时截断到 100
     */
    List<AuditEventRow> recentEvents(int limit);

    /**
     * 带过滤条件的审计事件，按入库顺序从新到旧。三个条件互相 AND，空串表示不参与过滤。
     *
     * <p>默认实现忽略过滤条件、退回「最近事件」，这样只在管理进程里接的一个查询口
     * 和测试替身都不用为了新条件改签名。真正带过滤的实现由 MySQL 版本覆盖。
     *
     * @param userId    操作者，可空
     * @param sessionId 会话，可空
     * @param runId     运行标识，可空
     * @param limit     条数。小于 1 时按 50，大于 100 时截断到 100
     */
    default List<AuditEventRow> events(String userId, String sessionId, String runId, int limit) {
        return recentEvents(limit);
    }
}

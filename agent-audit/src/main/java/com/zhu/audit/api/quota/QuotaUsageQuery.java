package com.zhu.audit.api.quota;

import java.util.List;

/**
 * 按租户查看配额消耗。不回补，也不改已有行。
 */
public interface QuotaUsageQuery {

    /**
     * 最近的流水。条数默认 50，最多 100。租户标识为空时拒绝。
     */
    List<QuotaUsageRow> recent(String tenantId, int limit);
}

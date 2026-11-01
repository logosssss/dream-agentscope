package com.zhu.audit.api;

import java.util.List;

/**
 * 管理进程登记和查看数据保留策略。不执行清理，也不改已有行。
 */
public interface RetentionPolicyCatalog {

    /**
     * 新增一条。同一数据类型和范围再登记一次是冲突。
     */
    RetentionPolicySummary register(String dataType, String scope, Integer retainDays, String action);

    /**
     * 最近登记的保留策略，含停用。条数默认由调用方给出，实现侧再压到上限。
     * 不执行清理。
     */
    List<RetentionPolicySummary> recent(int limit);
}

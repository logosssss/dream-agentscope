package com.zhu.audit.api;

/**
 * 登记数据保留策略。不执行清理，也不改已有行。
 */
public interface RetentionPolicyCatalog {

    /**
     * 新增一条。同一数据类型和范围再登记一次是冲突。
     */
    RetentionPolicySummary register(String dataType, String scope, Integer retainDays, String action);
}

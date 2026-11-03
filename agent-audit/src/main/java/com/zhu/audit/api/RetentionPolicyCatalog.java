package com.zhu.audit.api;

import java.util.List;

/**
 * 管理进程登记、停用、恢复和查看数据保留策略。不执行清理，也不改已有业务数据。
 */
public interface RetentionPolicyCatalog {

    /**
     * 新增一条。同一数据类型和范围再登记一次是冲突。
     */
    RetentionPolicySummary register(String dataType, String scope, Integer retainDays, String action);

    /**
     * 停用仍启用的策略。没有这一行或已停用由实现返回失败。
     * 空白范围按全局。不执行清理。
     */
    RetentionPolicySummary disable(String dataType, String scope);

    /**
     * 恢复已停用的策略。没有这一行或已启用由实现返回失败。
     * 空白范围按全局。不执行清理。
     */
    RetentionPolicySummary restore(String dataType, String scope);

    /**
     * 最近登记的保留策略，含停用。条数默认由调用方给出，实现侧再压到上限。
     * 不执行清理。
     */
    List<RetentionPolicySummary> recent(int limit);
}

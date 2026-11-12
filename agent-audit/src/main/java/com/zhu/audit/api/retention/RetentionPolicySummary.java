package com.zhu.audit.api.retention;

/**
 * 一条已登记的数据保留策略。不表示已经按它清理过数据。
 *
 * @param dataType   数据类型
 * @param scope      租户范围，* 表示全局
 * @param retainDays 保留天数
 * @param action     ARCHIVE / DELETE / ANONYMIZE
 * @param enabled    是否仍启用；停用后仍不执行清理
 */
public record RetentionPolicySummary(
        String dataType, String scope, int retainDays, String action, boolean enabled) {
}

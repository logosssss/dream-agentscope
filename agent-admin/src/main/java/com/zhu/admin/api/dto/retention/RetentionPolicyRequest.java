package com.zhu.admin.api.dto.retention;

/**
 * 登记一条数据保留策略。不触发清理。
 *
 * @param dataType   SESSION / MESSAGE / EVENT / ATTACHMENT
 * @param scope      租户范围，空白按全局
 * @param retainDays 保留天数，从 1 起
 * @param action     ARCHIVE / DELETE / ANONYMIZE，空白按 ARCHIVE
 */
public record RetentionPolicyRequest(String dataType, String scope, Integer retainDays, String action) {
}

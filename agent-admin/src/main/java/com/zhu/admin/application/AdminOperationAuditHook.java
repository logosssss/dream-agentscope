package com.zhu.admin.application;

import com.zhu.audit.api.event.OperationAuditCatalog;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 管理进程在写入成功后补记后台操作。不改业务结果，也不写前后快照。
 */
@Service
public class AdminOperationAuditHook {

    /** 还没有用户登录时，没有请求头就记成这个操作人。 */
    static final String DEFAULT_OPERATOR = "admin";

    private static final int OPERATOR_LIMIT = 128;

    private final ObjectProvider<OperationAuditCatalog> catalogs;

    /** MySQL 关闭时没有目录，直接跳过。 */
    public AdminOperationAuditHook(ObjectProvider<OperationAuditCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 只在业务已经成功时写。目录缺失、路径认不出或写入失败都不抛给调用方。
     */
    public void afterSuccess(String method, String path, String operatorId) {
        OperationAuditCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            return;
        }
        AdminOperationAuditTarget target = AdminOperationAuditTarget.parse(method, path);
        if (target == null) {
            return;
        }
        String operator = resolveOperator(operatorId);
        if (operator == null) {
            return;
        }
        try {
            catalog.record(
                    operator,
                    target.tenantId(),
                    target.operation(),
                    target.objectType(),
                    target.objectId());
        } catch (RuntimeException ignored) {
            // 审计写失败不能把已经成功的管理操作改成失败。
        }
    }

    /** 空白用默认操作人。超长宁可不记，避免目录校验把这次请求打成失败。 */
    private static String resolveOperator(String operatorId) {
        if (operatorId == null || operatorId.isBlank()) {
            return DEFAULT_OPERATOR;
        }
        String trimmed = operatorId.strip();
        if (trimmed.length() > OPERATOR_LIMIT) {
            return null;
        }
        return trimmed;
    }
}

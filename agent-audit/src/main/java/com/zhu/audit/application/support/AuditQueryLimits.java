package com.zhu.audit.application.support;

/**
 * 查询条数的上限。避免一次把审计大表整页拉进管理进程。
 */
public final class AuditQueryLimits {

    private AuditQueryLimits() {
    }

    /** 未指定时用 50，调用方传得再大也只取 100。 */
    public static int normalize(int limit) {
        if (limit < 1) {
            return 50;
        }
        return Math.min(limit, 100);
    }
}

package com.zhu.admin.application;

/**
 * 管理进程写入路径解析出的审计对象。只描述记哪一行，不改业务结果。
 *
 * @param tenantId   能从路径看出的租户，没有时是空串
 * @param operation  CREATE / UPDATE / SUSPEND 等
 * @param objectType 对象类型
 * @param objectId   对象标识，没有时为空
 */
public record AdminOperationAuditTarget(
        String tenantId,
        String operation,
        String objectType,
        String objectId) {

    /**
     * 只认管理进程已经挂上的写入路径。查询、手工登记审计和认不出的路径返回空。
     */
    public static AdminOperationAuditTarget parse(String method, String path) {
        if (method == null || !"POST".equalsIgnoreCase(method.strip()) || path == null || path.isBlank()) {
            return null;
        }
        String normalized = normalize(path);
        if (!normalized.startsWith("/api/admin/")) {
            return null;
        }
        String rest = normalized.substring("/api/admin/".length());
        if (rest.isEmpty() || rest.startsWith("operation-audits")) {
            return null;
        }
        String[] parts = rest.split("/");
        return switch (parts[0]) {
            case "tenants" -> tenant(parts);
            case "tenant-users" -> tenantUser(parts);
            case "tenant-quotas" -> tenantQuota(parts);
            case "tenant-token-quotas" -> tenantQuota(parts);
            case "tenant-cost-quotas" -> tenantQuota(parts);
            case "tenant-concurrent-quotas" -> tenantQuota(parts);
            case "model-prices" -> createOnly(parts, "model_price");
            case "model-configs" -> modelConfig(parts);
            case "tool-registries" -> toolRegistry(parts);
            case "tool-permissions" -> toolPermission(parts);
            case "retention-policies" -> retentionPolicy(parts);
            case "task-schedules" -> createOnly(parts, "task_schedule");
            case "eval-datasets" -> evalDataset(parts);
            case "eval-runs" -> evalRun(parts);
            case "billing" -> billing(parts);
            case "approvals" -> decision(parts, "approval");
            case "deletion-requests" -> decision(parts, "deletion_request");
            default -> null;
        };
    }

    /** 去掉查询串和末尾斜杠，避免同一接口因写法不同漏记。 */
    private static String normalize(String path) {
        int query = path.indexOf('?');
        String raw = query < 0 ? path : path.substring(0, query);
        if (raw.length() > 1 && raw.endsWith("/")) {
            return raw.substring(0, raw.length() - 1);
        }
        return raw;
    }

    /** 登记没有路径标识。停用、关闭和恢复带租户标识。 */
    private static AdminOperationAuditTarget tenant(String[] parts) {
        if (parts.length == 1) {
            return new AdminOperationAuditTarget("", "CREATE", "tenant", null);
        }
        if (parts.length == 3 && ("suspend".equals(parts[2]) || "close".equals(parts[2]) || "restore".equals(parts[2]))) {
            return identified(parts[1], parts[2].toUpperCase(), "tenant", parts[1]);
        }
        return null;
    }

    /** 登记没有路径标识。停用和恢复带租户与用户。 */
    private static AdminOperationAuditTarget tenantUser(String[] parts) {
        if (parts.length == 1) {
            return new AdminOperationAuditTarget("", "CREATE", "tenant_user", null);
        }
        if (parts.length == 4 && ("disable".equals(parts[3]) || "restore".equals(parts[3]))) {
            return identified(parts[1], parts[3].toUpperCase(), "tenant_user", parts[2]);
        }
        return null;
    }

    /** 登记没有路径标识。改上限只认已有租户这一行。 */
    private static AdminOperationAuditTarget tenantQuota(String[] parts) {
        if (parts.length == 1) {
            return new AdminOperationAuditTarget("", "CREATE", "tenant_quota", null);
        }
        if (parts.length == 2) {
            return identified(parts[1], "UPDATE", "tenant_quota", parts[1]);
        }
        return null;
    }

    /** 数据集登记和追加用例分开记，避免把用例写成数据集。 */
    private static AdminOperationAuditTarget evalDataset(String[] parts) {
        if (parts.length == 1) {
            return new AdminOperationAuditTarget("", "CREATE", "eval_dataset", null);
        }
        if (parts.length == 3 && "cases".equals(parts[2])) {
            return identified("", "CREATE", "eval_case", parts[1]);
        }
        return null;
    }

    /** 开跑没有路径标识。打分带执行标识。 */
    private static AdminOperationAuditTarget evalRun(String[] parts) {
        if (parts.length == 1) {
            return new AdminOperationAuditTarget("", "CREATE", "eval_run", null);
        }
        if (parts.length == 3 && "score".equals(parts[2])) {
            return identified("", "SCORE", "eval_run", parts[1]);
        }
        return null;
    }

    /** 账单四步都走同一对象类型，动作用路径末段区分。 */
    private static AdminOperationAuditTarget billing(String[] parts) {
        if (parts.length != 2) {
            return null;
        }
        return switch (parts[1]) {
            case "drafts" -> new AdminOperationAuditTarget("", "CREATE", "billing", null);
            case "confirmations" -> new AdminOperationAuditTarget("", "CONFIRM", "billing", null);
            case "invoices" -> new AdminOperationAuditTarget("", "INVOICE", "billing", null);
            case "payments" -> new AdminOperationAuditTarget("", "PAY", "billing", null);
            default -> null;
        };
    }

    /** 审批和删除决定都是 DECIDE，对象类型分开。 */
    private static AdminOperationAuditTarget decision(String[] parts, String objectType) {
        if (parts.length == 3 && "decision".equals(parts[2])) {
            return identified("", "DECIDE", objectType, parts[1]);
        }
        return null;
    }

    /** 登记没有路径标识。停用和恢复带配置标识。 */
    private static AdminOperationAuditTarget modelConfig(String[] parts) {
        if (parts.length == 1) {
            return new AdminOperationAuditTarget("", "CREATE", "model_config", null);
        }
        if (parts.length == 3 && ("disable".equals(parts[2]) || "restore".equals(parts[2]))) {
            return identified("", parts[2].toUpperCase(), "model_config", parts[1]);
        }
        return null;
    }

    /** 登记没有路径标识。停用和恢复带工具标识。 */
    private static AdminOperationAuditTarget toolRegistry(String[] parts) {
        if (parts.length == 1) {
            return new AdminOperationAuditTarget("", "CREATE", "tool_registry", null);
        }
        if (parts.length == 3 && ("disable".equals(parts[2]) || "restore".equals(parts[2]))) {
            return identified("", parts[2].toUpperCase(), "tool_registry", parts[1]);
        }
        return null;
    }

    /** 登记没有路径标识。停用、恢复和改优先级带工具标识。 */
    private static AdminOperationAuditTarget toolPermission(String[] parts) {
        if (parts.length == 1) {
            return new AdminOperationAuditTarget("", "CREATE", "tool_permission", null);
        }
        if (parts.length == 3
                && ("disable".equals(parts[2]) || "restore".equals(parts[2]) || "priority".equals(parts[2]))) {
            String operation = "priority".equals(parts[2]) ? "UPDATE" : parts[2].toUpperCase();
            return identified("", operation, "tool_permission", parts[1]);
        }
        return null;
    }

    /** 登记没有路径标识。停用和恢复带数据类型。范围在请求体里，不进路径。 */
    private static AdminOperationAuditTarget retentionPolicy(String[] parts) {
        if (parts.length == 1) {
            return new AdminOperationAuditTarget("", "CREATE", "retention_policy", null);
        }
        if (parts.length == 3 && ("disable".equals(parts[2]) || "restore".equals(parts[2]))) {
            return identified("", parts[2].toUpperCase(), "retention_policy", parts[1]);
        }
        return null;
    }

    /** 只有集合路径，没有对象标识。 */
    private static AdminOperationAuditTarget createOnly(String[] parts, String objectType) {
        if (parts.length == 1) {
            return new AdminOperationAuditTarget("", "CREATE", objectType, null);
        }
        return null;
    }

    /** 对象标识空白时宁可不记。租户空白按空串，和手工登记一致。 */
    private static AdminOperationAuditTarget identified(
            String tenantId,
            String operation,
            String objectType,
            String objectId) {
        if (objectId == null || objectId.isBlank()) {
            return null;
        }
        String savedTenant = tenantId == null || tenantId.isBlank() ? "" : tenantId;
        return new AdminOperationAuditTarget(savedTenant, operation, objectType, objectId);
    }
}

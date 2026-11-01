package com.zhu.vector.api;

/**
 * 情景记忆的过滤条件。租户、用户和 Agent 都必须带上。
 */
public final class MemoryFilter {

    /** 租户字段。检索条件不能空。 */
    public static final String TENANT = "tenant_id";

    private MemoryFilter() {
    }

    /**
     * 拼出 Milvus 的过滤表达式。用户和 Agent 不能空。
     * 没带租户按空串，仍限制在无租户这一档，不会扫到别人。
     * 引号和反斜杠要转义，避免标识里的引号改写条件。
     */
    public static String of(String tenantId, String userId, String agentId) {
        if (userId == null || userId.isBlank() || agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("user and agent are required");
        }
        String tenant = tenantId == null || tenantId.isBlank() ? "" : tenantId;
        return TENANT + " == \"" + escape(tenant) + "\" && " + KnowledgeFilter.of(userId, agentId);
    }

    /** 只转义表达式里的特殊字符，不改原标识的含义。 */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

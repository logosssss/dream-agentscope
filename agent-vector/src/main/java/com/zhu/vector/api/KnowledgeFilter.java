package com.zhu.vector.api;

/**
 * 知识检索的过滤条件。用户和 Agent 都必须带上，避免串到别人的向量。
 */
public final class KnowledgeFilter {

    /** 向量字段。维度与嵌入模型一致，默认 1024。 */
    public static final String VECTOR = "vector";

    /** 用户字段。检索条件不能空。 */
    public static final String USER = "user_id";

    /** Agent 字段。检索条件不能空。 */
    public static final String AGENT = "agent_id";

    private KnowledgeFilter() {
    }

    /**
     * 拼出 Milvus 的过滤表达式。用户或 Agent 为空时拒绝。
     * 引号和反斜杠要转义，避免标识里的引号改写条件。
     */
    public static String of(String userId, String agentId) {
        if (userId == null || userId.isBlank() || agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("user and agent are required");
        }
        return USER + " == \"" + escape(userId) + "\" && " + AGENT + " == \"" + escape(agentId) + "\"";
    }

    /** 只转义表达式里的特殊字符，不改原标识的含义。 */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

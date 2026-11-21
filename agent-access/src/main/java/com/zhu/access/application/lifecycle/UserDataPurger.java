package com.zhu.access.application.lifecycle;

import com.zhu.objectstore.api.ObjectStore;
import com.zhu.vector.api.VectorStore;
import com.zhu.vector.infrastructure.MilvusCollections;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 真正执行一次用户数据的删除。
 *
 * <p>放在用户进程而不是管理进程：附件对象和知识向量只有用户进程装配了
 * （管理进程按设计只探测 Milvus，不建集合），在这里才能把正文和向量一起清掉。
 *
 * <p>两类动作：
 * <ul>
 *   <li>硬删：会话、消息、运行记录、附件（含对象存储里的字节）、知识库（含向量）、
 *       情景记忆与画像、待办与反馈、登录凭据。</li>
 *   <li>脱敏保留：审计、安全干预、审批记录、token 用量、配额流水 —— 合规与计费要留行，
 *       只把 {@code user_id} 换成 {@code deleted:<摘要>}。</li>
 * </ul>
 *
 * <p>{@code dry-run} 打开时只数行数不删，报告里带 {@code dryRun=true}。
 * 单表行数超过上限时整表跳过，交给人工确认，避免一次误删整库。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class UserDataPurger {

    private static final String ANONYMIZED_PREFIX = "deleted:";

    private static final int SESSION_ID_BATCH = 200;

    private static final Set<String> DATA_TYPES = Set.of("SESSION", "ATTACHMENT", "ALL");

    private final JdbcTemplate jdbc;

    private final ObjectProvider<ObjectStore> objectStores;

    private final ObjectProvider<VectorStore> vectorStores;

    private final LifecycleProperties properties;

    private final String knowledgeCollection;

    /** 向量集合名与运行时检索共用同一个配置项。 */
    public UserDataPurger(
            JdbcTemplate jdbc,
            ObjectProvider<ObjectStore> objectStores,
            ObjectProvider<VectorStore> vectorStores,
            LifecycleProperties properties,
            @Value("${agentscope.milvus.collection:knowledge}") String knowledgeCollection) {
        this.jdbc = jdbc;
        this.objectStores = objectStores;
        this.vectorStores = vectorStores;
        this.properties = properties;
        this.knowledgeCollection = MilvusCollections.resolveKnowledge(knowledgeCollection);
    }

    /**
     * 按类型删一个用户的全部相关数据。
     *
     * @param tenantId 为空时按用户维度删（跨租户也删），非空时只删该租户
     * @param userId   被处理的用户
     * @param dataType SESSION / ATTACHMENT / ALL
     */
    public PurgeReport purge(String tenantId, String userId, String dataType) {
        if (userId == null || userId.isBlank() || dataType == null || !DATA_TYPES.contains(dataType)) {
            throw new IllegalArgumentException("deletion request is invalid");
        }
        String tenant = tenantId == null ? "" : tenantId.trim();
        boolean scoped = !tenant.isEmpty();
        boolean dryRun = properties.isDryRun();
        Map<String, Integer> deleted = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();

        List<String> sessions = sessionIds(tenant, userId, scoped);
        if (dataType.equals("SESSION") || dataType.equals("ALL")) {
            for (String session : sessions) {
                String suffix = scoped ? " AND tenant_id = ?" : "";
                String[] args = scoped
                        ? new String[]{session, tenant}
                        : new String[]{session};
                step("agent_run_step", deleted, skipped,
                        "SELECT COUNT(*) FROM agent_run_step WHERE run_id IN (SELECT run_id FROM agent_run WHERE session_id = ?" + suffix + ")",
                        "DELETE FROM agent_run_step WHERE run_id IN (SELECT run_id FROM agent_run WHERE session_id = ?" + suffix + ")",
                        args);
                step("chat_message", deleted, skipped,
                        "SELECT COUNT(*) FROM chat_message WHERE session_id = ?" + suffix,
                        "DELETE FROM chat_message WHERE session_id = ?" + suffix,
                        args);
                step("agent_run", deleted, skipped,
                        "SELECT COUNT(*) FROM agent_run WHERE session_id = ?" + suffix,
                        "DELETE FROM agent_run WHERE session_id = ?" + suffix,
                        args);
                step("agent_session_meta", deleted, skipped,
                        "SELECT COUNT(*) FROM agent_session_meta WHERE session_id = ?" + suffix,
                        "DELETE FROM agent_session_meta WHERE session_id = ?" + suffix,
                        args);
                step("agentscope_sessions", deleted, skipped,
                        "SELECT COUNT(*) FROM agentscope_sessions WHERE session_id = ?",
                        "DELETE FROM agentscope_sessions WHERE session_id = ?",
                        new String[]{session});
            }
            // 工作区文件按命名空间段匹配用户；段分隔符是 0x1F，避免 deleted-user-1 命中 deleted-user-10。
            String storeCount = "SELECT COUNT(*) FROM agentscope_store WHERE namespace_path LIKE CONCAT('%', ?, CHAR(31), '%')"
                    + " OR namespace_path LIKE CONCAT(?, CHAR(31), '%')";
            String storeDelete = "DELETE FROM agentscope_store WHERE namespace_path LIKE CONCAT('%', ?, CHAR(31), '%')"
                    + " OR namespace_path LIKE CONCAT(?, CHAR(31), '%')";
            if (!sessions.isEmpty()) {
                step("agentscope_store", deleted, skipped, storeCount, storeDelete,
                        new String[]{userId, userId});
            }
            step("agent_task", deleted, skipped,
                    tenantWhere("SELECT COUNT(*) FROM agent_task", scoped),
                    tenantWhere("DELETE FROM agent_task", scoped),
                    identityArgs(tenant, userId, scoped));
        }

        if (dataType.equals("ATTACHMENT") || dataType.equals("ALL")) {
            List<Map<String, Object>> attachments = jdbc.queryForList(
                    tenantWhere("SELECT storage_type, storage_path FROM attachment", scoped),
                    identityArgs(tenant, userId, scoped));
            if (!dryRun) {
                deleteBlobs(attachments, skipped);
            }
            step("attachment_ref", deleted, skipped,
                    "SELECT COUNT(*) FROM attachment_ref WHERE attachment_id IN "
                            + "(SELECT attachment_id FROM attachment WHERE user_id = ?"
                            + (scoped ? " AND tenant_id = ?" : "") + ")",
                    "DELETE FROM attachment_ref WHERE attachment_id IN "
                            + "(SELECT attachment_id FROM attachment WHERE user_id = ?"
                            + (scoped ? " AND tenant_id = ?" : "") + ")",
                    identityArgs(tenant, userId, scoped));
            step("attachment", deleted, skipped,
                    tenantWhere("SELECT COUNT(*) FROM attachment", scoped),
                    tenantWhere("DELETE FROM attachment", scoped),
                    identityArgs(tenant, userId, scoped));
        }

        if (dataType.equals("ALL")) {
            for (String agent : knowledgeAgents(tenant, userId, scoped)) {
                deleteKnowledgeVectors(tenant, userId, agent, dryRun, skipped, deleted);
            }
            step("kb_chunk", deleted, skipped,
                    tenantWhere("SELECT COUNT(*) FROM kb_chunk", scoped),
                    tenantWhere("DELETE FROM kb_chunk", scoped),
                    identityArgs(tenant, userId, scoped));
            step("kb_document", deleted, skipped,
                    tenantWhere("SELECT COUNT(*) FROM kb_document", scoped),
                    tenantWhere("DELETE FROM kb_document", scoped),
                    identityArgs(tenant, userId, scoped));
            step("kb_knowledge_base", deleted, skipped,
                    tenantWhere("SELECT COUNT(*) FROM kb_knowledge_base", scoped),
                    tenantWhere("DELETE FROM kb_knowledge_base", scoped),
                    identityArgs(tenant, userId, scoped));
            step("episodic_memory", deleted, skipped,
                    tenantWhere("SELECT COUNT(*) FROM episodic_memory", scoped),
                    tenantWhere("DELETE FROM episodic_memory", scoped),
                    identityArgs(tenant, userId, scoped));
            step("user_profile", deleted, skipped,
                    tenantWhere("SELECT COUNT(*) FROM user_profile", scoped),
                    tenantWhere("DELETE FROM user_profile", scoped),
                    identityArgs(tenant, userId, scoped));
            step("user_todo", deleted, skipped,
                    "SELECT COUNT(*) FROM user_todo WHERE user_id = ?",
                    "DELETE FROM user_todo WHERE user_id = ?",
                    new String[]{userId});
            step("user_feedback", deleted, skipped,
                    "SELECT COUNT(*) FROM user_feedback WHERE user_id = ?",
                    "DELETE FROM user_feedback WHERE user_id = ?",
                    new String[]{userId});
            step("login_credential", deleted, skipped,
                    tenantWhere("SELECT COUNT(*) FROM login_credential", scoped),
                    tenantWhere("DELETE FROM login_credential", scoped),
                    identityArgs(tenant, userId, scoped));

            String anonymized = ANONYMIZED_PREFIX + anonymizedSuffix(userId);
            for (String table : anonymizeTables()) {
                anonymize(table, anonymized, tenant, userId, scoped, deleted, skipped);
            }
            markTenantUserDeleted(tenant, userId, scoped, deleted, skipped);
        }

        return new PurgeReport(userId, dataType, dryRun, deleted, skipped);
    }

    /** 只读会话标识，供逐会话删除用。 */
    private List<String> sessionIds(String tenant, String userId, boolean scoped) {
        return jdbc.queryForList(
                tenantWhere("SELECT session_id FROM agent_session_meta", scoped),
                String.class,
                identityArgs(tenant, userId, scoped));
    }

    /** 知识库里有向量的产品 Agent（同一用户可能建过多个 Agent 的库）。 */
    private List<String> knowledgeAgents(String tenant, String userId, boolean scoped) {
        return jdbc.queryForList(
                tenantWhere("SELECT DISTINCT agent_id FROM kb_document", scoped),
                String.class,
                identityArgs(tenant, userId, scoped));
    }

    /** 附件库里真实的字节。存储类型不匹配的跳过，由运维手工对账。 */
    private void deleteBlobs(List<Map<String, Object>> attachments, List<String> skipped) {
        ObjectStore store = objectStores.getIfAvailable();
        if (store == null) {
            skipped.add("attachment blob: object store not configured");
            return;
        }
        int failed = 0;
        for (Map<String, Object> row : attachments) {
            String type = row.get("storage_type") == null ? "" : row.get("storage_type").toString();
            String path = row.get("storage_path") == null ? "" : row.get("storage_path").toString();
            if (!type.equalsIgnoreCase(store.storageType()) || path.isBlank()) {
                failed++;
                continue;
            }
            try {
                store.delete(path);
            } catch (RuntimeException ex) {
                // 单个对象删不掉不挡整批：行照样删，报告里记下来。
                failed++;
            }
        }
        if (failed > 0) {
            skipped.add("attachment blob: " + failed + " not removed");
        }
    }

    /** 向量按 (用户, 产品 Agent) 组合删，与检索过滤条件一致。 */
    private void deleteKnowledgeVectors(
            String tenant, String userId, String agentId, boolean dryRun,
            List<String> skipped, Map<String, Integer> deleted) {
        VectorStore vectors = vectorStores.getIfAvailable();
        if (vectors == null) {
            skipped.add("kb vectors: vector store not configured");
            return;
        }
        if (dryRun) {
            deleted.merge("kb_vectors[" + agentId + "]", 0, Integer::sum);
            return;
        }
        try {
            vectors.deleteByScope(knowledgeCollection, tenant, userId, agentId);
            deleted.merge("kb_vectors[" + agentId + "]", 1, Integer::sum);
        } catch (RuntimeException ex) {
            skipped.add("kb vectors[" + agentId + "]: delete failed");
        }
    }

    /** 需要脱敏保留的表。 */
    private static List<String> anonymizeTables() {
        return List.of("agent_event_audit", "safety_intervention", "approval_record",
                "quota_usage_log", "token_usage");
    }

    /** 审计类表把 user_id 换掉，其余列不动。 */
    private void anonymize(
            String table, String anonymized, String tenant, String userId, boolean scoped,
            Map<String, Integer> deleted, List<String> skipped) {
        long count = count(tenantWhere("SELECT COUNT(*) FROM " + table, scoped), identityArgs(tenant, userId, scoped));
        if (count == 0) {
            return;
        }
        if (count > properties.getMaxRowsPerTable()) {
            skipped.add(table + " anonymize: rows above max");
            return;
        }
        if (properties.isDryRun()) {
            deleted.merge(table + "(anonymize)", (int) count, Integer::sum);
            return;
        }
        String sql = tenantWhere("UPDATE " + table + " SET user_id = ?", scoped);
        List<Object> args = new ArrayList<>();
        args.add(anonymized);
        args.add(userId);
        if (scoped) {
            args.add(tenant);
        }
        int rows = jdbc.update(sql, args.toArray());
        deleted.merge(table + "(anonymize)", rows, Integer::sum);
    }

    /** 保留行但标成 DELETED：租户侧还能看到「这个人删过号」。 */
    private void markTenantUserDeleted(
            String tenant, String userId, boolean scoped,
            Map<String, Integer> deleted, List<String> skipped) {
        long count = count(tenantWhere("SELECT COUNT(*) FROM tenant_user", scoped), identityArgs(tenant, userId, scoped));
        if (count == 0) {
            return;
        }
        if (properties.isDryRun()) {
            deleted.merge("tenant_user(disable)", (int) count, Integer::sum);
            return;
        }
        String sql = tenantWhere("UPDATE tenant_user SET status = 'DELETED'", scoped);
        int rows = jdbc.update(sql, identityArgs(tenant, userId, scoped));
        deleted.merge("tenant_user(disable)", rows, Integer::sum);
    }

    /** 一条「计数 → 删」的步骤，dry-run 与上限都在这里统一拦。 */
    private void step(
            String name, Map<String, Integer> deleted, List<String> skipped,
            String countSql, String deleteSql, Object[] args) {
        long count = count(countSql, args);
        if (count == 0) {
            return;
        }
        if (count > properties.getMaxRowsPerTable()) {
            skipped.add(name + ": rows above max");
            return;
        }
        if (properties.isDryRun()) {
            deleted.merge(name, (int) count, Integer::sum);
            return;
        }
        int rows = jdbc.update(deleteSql, args);
        deleted.merge(name, rows, Integer::sum);
    }

    private long count(String sql, Object[] args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private static String tenantWhere(String base, boolean scoped) {
        return base + " WHERE user_id = ?" + (scoped ? " AND tenant_id = ?" : "");
    }

    private static Object[] identityArgs(String tenant, String userId, boolean scoped) {
        return scoped ? new Object[]{userId, tenant} : new Object[]{userId};
    }

    /** 脱敏后的标识：不可逆，且同一用户稳定，便于跨表对账。 */
    private static String anonymizedSuffix(String userId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(userId.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            // 摘要算法一定存在；真缺了也不能把明文写回库里。
            return Integer.toHexString(userId.hashCode());
        }
    }
}

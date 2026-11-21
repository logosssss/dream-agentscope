package com.zhu.access.application.lifecycle;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 保留策略里的 {@code data_type} 到「表 + 时间列」的映射。
 *
 * <p>只放映射，不碰数据库，方便单独测。策略表允许写 ARCHIVE / ANONYMIZE，
 * 但这次只执行 DELETE —— 另外两种没有真实的归档与脱敏管线，登记了也不该假装执行。
 */
public final class RetentionTargets {

    /**
     * 一张可按时间清理的表。
     *
     * @param dataType     策略里的类型
     * @param table        表名
     * @param timeColumn   判断超期用的时间列
     * @param tenantScoped 表上有没有 tenant_id，决定要不要按租户圈定
     */
    public record Target(String dataType, String table, String timeColumn, boolean tenantScoped) {
    }

    private static final Map<String, Target> TARGETS = Map.of(
            "SESSION", new Target("SESSION", "agent_session_meta", "last_active_at", true),
            "MESSAGE", new Target("MESSAGE", "chat_message", "created_at", true),
            "EVENT", new Target("EVENT", "agent_event_audit", "created_at", false),
            "ATTACHMENT", new Target("ATTACHMENT", "attachment", "created_at", true));

    private RetentionTargets() {
    }

    /** 按类型取目标；不认识或这次不执行的类型返回空。 */
    public static Optional<Target> of(String dataType) {
        if (dataType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(TARGETS.get(dataType.trim()));
    }

    /** 这次支持清理的类型。 */
    public static Set<String> supported() {
        return TARGETS.keySet();
    }
}

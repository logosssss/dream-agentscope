package com.zhu.state.application;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.state.api.UserProfileRepository;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 把用户画像写入 MySQL。只在存储开关打开时创建。同一租户用户覆盖阶段和备注。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlUserProfileRepository implements UserProfileRepository {

    private static final int TENANT_LIMIT = 64;

    private static final int USER_LIMIT = 128;

    private static final int NOTES_LIMIT = 1000;

    private static final Set<String> STAGES = Set.of("ACQUAINTANCE", "FAMILIAR", "CLOSE");

    private final JdbcTemplate jdbc;

    /** 写入走 JDBC。校验失败不写库。 */
    public MysqlUserProfileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 空白租户按空串。空白备注留空。不截断超长字段。
     * 阶段写错时拒绝，避免关系被随便升级。
     */
    @Override
    public void upsert(String tenantId, String userId, String relationshipStage, String notes) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)
                || relationshipStage == null || !STAGES.contains(relationshipStage)
                || (notes != null && notes.length() > NOTES_LIMIT)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "user profile is invalid");
        }
        jdbc.update(
                "INSERT INTO user_profile (tenant_id, user_id, relationship_stage, notes) VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE relationship_stage = VALUES(relationship_stage), notes = VALUES(notes)",
                tenantId == null || tenantId.isBlank() ? "" : tenantId,
                userId,
                relationshipStage,
                notes == null || notes.isBlank() ? null : notes);
    }
}

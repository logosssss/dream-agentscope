package com.zhu.state.infrastructure.persistence;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.state.api.UserProfileItem;
import com.zhu.state.api.UserProfileRepository;
import com.zhu.state.infrastructure.persistence.entity.UserProfile;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 把用户画像写入 MySQL，并按身份读回。只在存储开关打开时创建。
 * 同一租户用户覆盖阶段和备注。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlUserProfileRepository implements UserProfileRepository {

    private static final int TENANT_LIMIT = 64;

    private static final int USER_LIMIT = 128;

    private static final int NOTES_LIMIT = 1000;

    private static final Set<String> STAGES = Set.of("ACQUAINTANCE", "FAMILIAR", "CLOSE");

    private final UserProfileMapper profiles;

    /** 覆盖写走 Mapper。校验失败不写库。 */
    public MysqlUserProfileRepository(UserProfileMapper profiles) {
        this.profiles = profiles;
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
        UserProfile row = new UserProfile();
        row.setTenantId(tenantId == null || tenantId.isBlank() ? "" : tenantId);
        row.setUserId(userId);
        row.setRelationshipStage(relationshipStage);
        row.setNotes(notes == null || notes.isBlank() ? null : notes);
        profiles.upsert(row);
    }

    /**
     * 空白租户按空串。没有行或用户为空时返回 null。
     */
    @Override
    public UserProfileItem find(String tenantId, String userId) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)) {
            return null;
        }
        UserProfile row = profiles.find(tenantId == null || tenantId.isBlank() ? "" : tenantId, userId);
        if (row == null) {
            return null;
        }
        return new UserProfileItem(
                row.getTenantId() == null ? "" : row.getTenantId(),
                row.getUserId(),
                row.getRelationshipStage(),
                row.getNotes());
    }
}

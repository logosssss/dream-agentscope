package com.zhu.audit.application;

import com.zhu.audit.api.TokenUsageDetail;
import com.zhu.audit.api.TokenUsageQuery;
import com.zhu.audit.api.TokenUsageRow;
import com.zhu.audit.infrastructure.persistence.TokenUsageMapper;
import com.zhu.audit.infrastructure.persistence.entity.TokenUsage;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 按用户和租户列出 token 用量。只在存储开关打开时创建。不改已有行。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlTokenUsageQuery implements TokenUsageQuery {

    private static final int USER_LIMIT = 128;

    private static final int TENANT_LIMIT = 64;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final TokenUsageMapper usages;

    /** 列表用 Mapper。空结果返回空列表，不抛找不到。 */
    public MysqlTokenUsageQuery(TokenUsageMapper usages) {
        this.usages = usages;
    }

    /**
     * 空用户拒绝。空白租户按空串，和写入时一致。
     */
    @Override
    public List<TokenUsageRow> owned(String userId, String tenantId, int limit) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "usage query is invalid");
        }
        String savedTenant = tenantId == null || tenantId.isBlank() ? "" : tenantId;
        List<TokenUsage> rows = usages.listOwned(userId, savedTenant, AuditQueryLimits.normalize(limit));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(MysqlTokenUsageQuery::row).toList();
    }

    /**
     * 空用户或非法主键拒绝。找不到或不是本人本租户按找不到。
     * 不改金额，也不读提示词。
     */
    @Override
    public TokenUsageDetail ownedOne(String userId, String tenantId, Long usageId) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)
                || usageId == null || usageId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "usage query is invalid");
        }
        String savedTenant = tenantId == null || tenantId.isBlank() ? "" : tenantId;
        TokenUsage usage = usages.findOwned(usageId, userId, savedTenant);
        if (usage == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "usage not found");
        }
        String tenant = usage.getTenantId() == null ? "" : usage.getTenantId();
        String created = usage.getCreatedAt() == null ? "" : TIME.format(usage.getCreatedAt());
        return new TokenUsageDetail(
                usage.getId(),
                usage.getRunId(),
                tenant,
                usage.getSessionId(),
                usage.getModel(),
                usage.getInputTokens(),
                usage.getOutputTokens(),
                usage.getTotalTokens(),
                usage.getCostAmount(),
                usage.getCurrency(),
                created);
    }

    /** 列表不带回写入时间，调用方用运行标识对照。 */
    private static TokenUsageRow row(TokenUsage usage) {
        String tenant = usage.getTenantId() == null ? "" : usage.getTenantId();
        return new TokenUsageRow(
                usage.getId(),
                usage.getRunId(),
                tenant,
                usage.getUserId(),
                usage.getSessionId(),
                usage.getModel(),
                usage.getInputTokens(),
                usage.getOutputTokens(),
                usage.getTotalTokens(),
                usage.getCostAmount(),
                usage.getCurrency());
    }
}

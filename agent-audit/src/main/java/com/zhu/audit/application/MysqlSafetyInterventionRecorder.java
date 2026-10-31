package com.zhu.audit.application;

import com.zhu.audit.api.SafetyInterventionRecorder;
import com.zhu.audit.infrastructure.persistence.SafetyInterventionMapper;
import com.zhu.audit.infrastructure.persistence.entity.SafetyIntervention;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 把安全干预写入 MySQL。只在存储开关打开时创建。不改已有行。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlSafetyInterventionRecorder implements SafetyInterventionRecorder {

    private static final int TENANT_LIMIT = 64;

    private static final int USER_LIMIT = 128;

    private static final int SESSION_LIMIT = 128;

    private static final int SUMMARY_LIMIT = 128;

    private static final Set<String> LEVELS = Set.of(LEVEL_ELEVATED, LEVEL_CRITICAL);

    private static final Set<String> SOURCES = Set.of(SOURCE_KEYWORD, SOURCE_SEMANTIC, SOURCE_JUDGE_FAILED);

    private final SafetyInterventionMapper interventions;

    /** 插入用 Mapper。校验失败不写库。 */
    public MysqlSafetyInterventionRecorder(SafetyInterventionMapper interventions) {
        this.interventions = interventions;
    }

    /**
     * 空白租户和会话按空串。不截断超长字段。
     * 普通陪伴不要调用这个方法。
     */
    @Override
    public void record(
            String tenantId,
            String userId,
            String sessionId,
            String riskLevel,
            String source,
            String summary) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)
                || (sessionId != null && sessionId.length() > SESSION_LIMIT)
                || riskLevel == null || !LEVELS.contains(riskLevel)
                || source == null || !SOURCES.contains(source)
                || summary == null || summary.isBlank() || summary.length() > SUMMARY_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "safety intervention is invalid");
        }
        SafetyIntervention row = new SafetyIntervention();
        row.setTenantId(tenantId == null || tenantId.isBlank() ? "" : tenantId);
        row.setUserId(userId);
        row.setSessionId(sessionId == null || sessionId.isBlank() ? "" : sessionId);
        row.setRiskLevel(riskLevel);
        row.setSource(source);
        row.setSummary(summary);
        interventions.insert(row);
    }
}

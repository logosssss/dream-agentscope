package com.zhu.audit.application;

import com.zhu.audit.api.RetentionPolicyCatalog;
import com.zhu.audit.api.RetentionPolicySummary;
import com.zhu.audit.infrastructure.persistence.DataRetentionPolicyMapper;
import com.zhu.audit.infrastructure.persistence.entity.DataRetentionPolicy;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 登记和列出数据保留策略。只在存储开关打开时创建。不跑清理任务。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlRetentionPolicyCatalog implements RetentionPolicyCatalog {

    private static final String GLOBAL_SCOPE = "*";

    private static final String DEFAULT_ACTION = "ARCHIVE";

    private static final int TYPE_LIMIT = 64;

    private static final int SCOPE_LIMIT = 64;

    private static final Set<String> DATA_TYPES = Set.of("SESSION", "MESSAGE", "EVENT", "ATTACHMENT");

    private static final Set<String> ACTIONS = Set.of("ARCHIVE", "DELETE", "ANONYMIZE");

    private final DataRetentionPolicyMapper policies;

    /** 同一数据类型和范围由唯一键拒绝。 */
    public MysqlRetentionPolicyCatalog(DataRetentionPolicyMapper policies) {
        this.policies = policies;
    }

    /**
     * 空白范围按全局。空白动作按 ARCHIVE。天数从 1 起。
     * 不校验类型名大小写以外的别名，也不执行清理。
     */
    @Override
    public RetentionPolicySummary register(String dataType, String scope, Integer retainDays, String action) {
        if (dataType == null || dataType.isBlank() || dataType.length() > TYPE_LIMIT
                || !DATA_TYPES.contains(dataType)
                || (scope != null && scope.length() > SCOPE_LIMIT)
                || retainDays == null || retainDays < 1
                || (action != null && !action.isBlank() && !ACTIONS.contains(action))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "retention policy is invalid");
        }
        String savedScope = scope == null || scope.isBlank() ? GLOBAL_SCOPE : scope;
        String savedAction = action == null || action.isBlank() ? DEFAULT_ACTION : action;
        DataRetentionPolicy row = new DataRetentionPolicy();
        row.setDataType(dataType);
        row.setScope(savedScope);
        row.setRetainDays(retainDays);
        row.setAction(savedAction);
        row.setEnabled(1);
        try {
            policies.insert(row);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "retention policy already exists");
        }
        return new RetentionPolicySummary(dataType, savedScope, retainDays, savedAction);
    }

    /**
     * 空结果返回空列表。不按启用再滤一层，停用也要能看见。
     * 不执行清理，也不改已有数据。
     */
    @Override
    public List<RetentionPolicySummary> recent(int limit) {
        List<DataRetentionPolicy> rows = policies.recent(AuditQueryLimits.normalize(limit));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new RetentionPolicySummary(
                        row.getDataType(),
                        row.getScope(),
                        row.getRetainDays() == null ? 0 : row.getRetainDays(),
                        row.getAction()))
                .toList();
    }
}

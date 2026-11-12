package com.zhu.audit.application.retention;

import com.zhu.audit.application.support.AuditQueryLimits;

import com.zhu.audit.api.retention.RetentionPolicyCatalog;
import com.zhu.audit.api.retention.RetentionPolicySummary;
import com.zhu.audit.infrastructure.persistence.retention.DataRetentionPolicyMapper;
import com.zhu.audit.infrastructure.persistence.entity.retention.DataRetentionPolicy;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 登记、停用、恢复和列出数据保留策略。只在存储开关打开时创建。不跑清理任务。
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
        ScopeKey key = scopeKey(dataType, scope);
        if (retainDays == null || retainDays < 1
                || (action != null && !action.isBlank() && !ACTIONS.contains(action))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "retention policy is invalid");
        }
        String savedAction = action == null || action.isBlank() ? DEFAULT_ACTION : action;
        DataRetentionPolicy row = new DataRetentionPolicy();
        row.setDataType(key.dataType());
        row.setScope(key.scope());
        row.setRetainDays(retainDays);
        row.setAction(savedAction);
        row.setEnabled(1);
        try {
            policies.insert(row);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "retention policy already exists");
        }
        return new RetentionPolicySummary(key.dataType(), key.scope(), retainDays, savedAction, true);
    }

    /**
     * 先确认存在且启用，再按启用行更新，避免覆盖已停用行。
     * 不执行清理。
     */
    @Override
    public RetentionPolicySummary disable(String dataType, String scope) {
        ScopeKey key = scopeKey(dataType, scope);
        DataRetentionPolicy existing = require(key);
        if (!Integer.valueOf(1).equals(existing.getEnabled())) {
            throw new BusinessException(ErrorCode.CONFLICT, "retention policy is not enabled");
        }
        int updated = policies.disableEnabled(key.dataType(), key.scope());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "retention policy is not enabled");
        }
        return summary(existing, false);
    }

    /**
     * 先确认存在且停用，再按停用行更新，避免覆盖已启用行。
     * 不执行清理。
     */
    @Override
    public RetentionPolicySummary restore(String dataType, String scope) {
        ScopeKey key = scopeKey(dataType, scope);
        DataRetentionPolicy existing = require(key);
        if (Integer.valueOf(1).equals(existing.getEnabled())) {
            throw new BusinessException(ErrorCode.CONFLICT, "retention policy is already enabled");
        }
        int updated = policies.restoreDisabled(key.dataType(), key.scope());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "retention policy is already enabled");
        }
        return summary(existing, true);
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
                        row.getAction(),
                        Integer.valueOf(1).equals(row.getEnabled())))
                .toList();
    }

    /** 只认已登记的数据类型；空白范围按全局。 */
    private ScopeKey scopeKey(String dataType, String scope) {
        if (dataType == null || dataType.isBlank() || dataType.length() > TYPE_LIMIT
                || !DATA_TYPES.contains(dataType)
                || (scope != null && scope.length() > SCOPE_LIMIT)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "retention policy is invalid");
        }
        String savedScope = scope == null || scope.isBlank() ? GLOBAL_SCOPE : scope;
        return new ScopeKey(dataType, savedScope);
    }

    /** 按类型和范围定位一行。 */
    private DataRetentionPolicy require(ScopeKey key) {
        DataRetentionPolicy existing = policies.findByKey(key.dataType(), key.scope());
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "retention policy not found");
        }
        return existing;
    }

    /** 用库里的字段，只改启用标记。 */
    private static RetentionPolicySummary summary(DataRetentionPolicy existing, boolean enabled) {
        return new RetentionPolicySummary(
                existing.getDataType(),
                existing.getScope() == null ? GLOBAL_SCOPE : existing.getScope(),
                existing.getRetainDays() == null ? 0 : existing.getRetainDays(),
                existing.getAction(),
                enabled);
    }

    /** 唯一键上的类型与范围。 */
    private record ScopeKey(String dataType, String scope) {
    }
}

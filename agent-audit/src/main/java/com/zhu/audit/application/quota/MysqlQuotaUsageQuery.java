package com.zhu.audit.application.quota;

import com.zhu.audit.application.support.AuditQueryLimits;

import com.zhu.audit.api.quota.QuotaUsageQuery;
import com.zhu.audit.api.quota.QuotaUsageRow;
import com.zhu.audit.infrastructure.persistence.quota.QuotaUsageLogMapper;
import com.zhu.audit.infrastructure.persistence.entity.quota.QuotaUsageLog;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 从 MySQL 读取配额消耗。只在存储开关打开时创建。不写回补。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlQuotaUsageQuery implements QuotaUsageQuery {

    private final QuotaUsageLogMapper logs;

    /** 查询用 Mapper。条数在这里收口，避免一次把流水整表拉进来。 */
    public MysqlQuotaUsageQuery(QuotaUsageLogMapper logs) {
        this.logs = logs;
    }

    /**
     * 空标识或超长标识拒绝。不截断标识，避免查到别的租户。
     */
    @Override
    public List<QuotaUsageRow> recent(String tenantId, int limit) {
        if (tenantId == null || tenantId.isBlank() || tenantId.length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tenantId is required");
        }
        return logs.recent(tenantId, AuditQueryLimits.normalize(limit)).stream()
                .map(MysqlQuotaUsageQuery::row)
                .toList();
    }

    /** 数量为空时按 0 返回，避免旧行把列表接口弄失败。 */
    private static QuotaUsageRow row(QuotaUsageLog log) {
        long amount = log.getAmount() == null ? 0L : log.getAmount();
        return new QuotaUsageRow(
                log.getId(),
                log.getTenantId(),
                log.getUserId(),
                log.getQuotaType(),
                amount,
                log.getReason(),
                log.getCreatedAt());
    }
}

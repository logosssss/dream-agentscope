package com.zhu.audit.application.quota;

import com.zhu.audit.api.quota.QuotaUsageWriter;
import com.zhu.audit.infrastructure.persistence.quota.QuotaUsageLogMapper;
import com.zhu.audit.infrastructure.persistence.entity.quota.QuotaUsageLog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 把一次已通过的每日请求写入 MySQL。只在存储开关打开时创建。不写运行标识，也不写负数。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlQuotaUsageWriter implements QuotaUsageWriter {

    private static final String REQUESTS = "REQUESTS";

    private static final int TENANT_LIMIT = 64;

    private static final int USER_LIMIT = 128;

    private static final int REASON_LIMIT = 128;

    private final QuotaUsageLogMapper logs;

    /** 插入用 Mapper。失败原样抛出，由额度检查决定是否影响这次请求。 */
    public MysqlQuotaUsageWriter(QuotaUsageLogMapper logs) {
        this.logs = logs;
    }

    /**
     * 数量固定为 1。空用户留空，空原因留空。超长字段截到列宽，避免流水插入失败。
     */
    @Override
    public void recordDailyRequest(String tenantId, String userId, String reason) {
        QuotaUsageLog row = new QuotaUsageLog();
        row.setTenantId(clip(tenantId == null || tenantId.isBlank() ? "" : tenantId, TENANT_LIMIT));
        row.setUserId(userId == null || userId.isBlank() ? null : clip(userId, USER_LIMIT));
        row.setQuotaType(REQUESTS);
        row.setAmount(1L);
        row.setReason(reason == null || reason.isBlank() ? null : clip(reason, REASON_LIMIT));
        logs.insert(row);
    }

    /** 列宽不够时只保留前一段。不改短于上限的原文。 */
    private static String clip(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}

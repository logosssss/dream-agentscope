package com.zhu.audit.infrastructure.persistence;

import com.zhu.audit.api.TokenUsageWriter;
import com.zhu.audit.infrastructure.persistence.entity.TokenUsage;
import com.zhu.common.util.Ids;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 把 token 用量写入 MySQL。金额固定为 0，因为还没有价格表。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlTokenUsageWriter implements TokenUsageWriter {

    private static final int MODEL_LIMIT = 128;

    private final TokenUsageMapper usages;

    /** MySQL 打开时由审计 Mapper 扫描提供实现。 */
    public MysqlTokenUsageWriter(TokenUsageMapper usages) {
        this.usages = usages;
    }

    /**
     * 用户或模型为空时不写，避免违反非空约束。
     * 负数 token 记成 0。运行标识为空时现生成一个。
     */
    @Override
    public void record(
            String tenantId,
            String userId,
            String sessionId,
            String runId,
            String model,
            long inputTokens,
            long outputTokens) {
        if (userId == null || userId.isBlank() || model == null || model.isBlank()) {
            return;
        }
        long input = Math.max(0L, inputTokens);
        long output = Math.max(0L, outputTokens);
        TokenUsage row = new TokenUsage();
        row.setRunId(runId == null || runId.isBlank() ? Ids.newId() : runId);
        row.setTenantId(tenantId == null ? "" : tenantId);
        row.setUserId(userId);
        row.setSessionId(sessionId == null || sessionId.isBlank() ? null : sessionId);
        row.setModel(model.length() <= MODEL_LIMIT ? model : model.substring(0, MODEL_LIMIT));
        row.setInputTokens(input);
        row.setOutputTokens(output);
        row.setTotalTokens(input + output);
        row.setCostAmount(BigDecimal.ZERO);
        row.setCurrency("CNY");
        usages.insert(row);
    }
}

package com.zhu.audit.application.usage;

import com.zhu.audit.api.usage.TokenUsageWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * MySQL 关闭时不写 token 用量。只给测试用，不在写库失败时改记到别处。
 */
@Service
@ConditionalOnProperty(
        name = "agentscope.distributed.mysql.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class AbsentTokenUsageWriter implements TokenUsageWriter {

    /** 没有表可写，直接跳过。 */
    @Override
    public void record(
            String tenantId,
            String userId,
            String sessionId,
            String runId,
            String model,
            long inputTokens,
            long outputTokens) {
    }
}

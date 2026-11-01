package com.zhu.access.infrastructure.persistence;

import com.zhu.runtime.api.SessionTurnCount;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 用 agent_run 的成功行数当会话轮次。MySQL 打开时才有这张表。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlSessionTurnCount implements SessionTurnCount {

    private final AgentRunMapper runs;

    /** Mapper 只在 MySQL 打开时存在。 */
    public MysqlSessionTurnCount(AgentRunMapper runs) {
        this.runs = runs;
    }

    /**
     * 用户或会话为空时不查。租户空按空串，和写入时一致。
     * 查失败抛给调用方，由中间件当成 0。
     */
    @Override
    public int succeeded(String tenantId, String userId, String sessionId) {
        if (userId == null || userId.isBlank() || sessionId == null || sessionId.isBlank()) {
            return 0;
        }
        return runs.countSucceeded(tenantId == null ? "" : tenantId, userId, sessionId);
    }
}

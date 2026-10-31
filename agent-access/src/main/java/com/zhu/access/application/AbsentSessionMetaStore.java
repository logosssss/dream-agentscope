package com.zhu.access.application;

import com.zhu.access.api.dto.SessionSummary;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * MySQL 关闭时的空实现。只给测试用，不在 Redis 失败时顶上。
 * 生产配置打开 MySQL，不会创建这个 Bean。
 */
@Service
@ConditionalOnProperty(
        name = "agentscope.distributed.mysql.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class AbsentSessionMetaStore implements SessionMetaStore {

    /** 没有表可写，直接跳过。 */
    @Override
    public void touch(String tenantId, String userId, String sessionId, String channel, String title) {
    }

    /** 没有表可读，返回空列表。 */
    @Override
    public List<SessionSummary> list(String tenantId, String userId) {
        return List.of();
    }

    /** 没有表可更新，直接跳过。 */
    @Override
    public void attachRun(String tenantId, String userId, String sessionId, String runId) {
    }
}

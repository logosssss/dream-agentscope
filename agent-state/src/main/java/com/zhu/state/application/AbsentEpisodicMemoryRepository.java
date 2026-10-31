package com.zhu.state.application;

import com.zhu.state.api.EpisodicMemoryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * MySQL 关闭时不写情景记忆。只给测试用，不改记到内存。
 */
@Service
@ConditionalOnProperty(
        name = "agentscope.distributed.mysql.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class AbsentEpisodicMemoryRepository implements EpisodicMemoryRepository {

    /** 没有表可写，直接跳过。 */
    @Override
    public void append(String tenantId, String userId, String sessionId, String summary, int salience) {
    }
}

package com.zhu.state.application;

import com.zhu.state.api.memory.EpisodicMemoryItem;
import com.zhu.state.api.memory.EpisodicMemoryRepository;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * MySQL 关闭时不写也不读情景记忆。只给测试用，不改记到内存。
 */
@Service
@ConditionalOnProperty(
        name = "agentscope.distributed.mysql.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class AbsentEpisodicMemoryRepository implements EpisodicMemoryRepository {

    /** 没有表可写，直接跳过。返回 0，调用方不要拿去写向量。 */
    @Override
    public long append(String tenantId, String userId, String sessionId, String summary, int salience) {
        return 0L;
    }

    /** 没有表可查，返回空列表。 */
    @Override
    public List<EpisodicMemoryItem> recent(String tenantId, String userId, int limit) {
        return List.of();
    }

    /** 没有表可查，返回空列表。 */
    @Override
    public List<EpisodicMemoryItem> findByIds(String tenantId, String userId, List<Long> ids) {
        return List.of();
    }
}

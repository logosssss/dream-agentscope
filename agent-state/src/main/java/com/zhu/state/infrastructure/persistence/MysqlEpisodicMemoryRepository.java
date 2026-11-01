package com.zhu.state.infrastructure.persistence;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.state.api.EpisodicMemoryItem;
import com.zhu.state.api.EpisodicMemoryRepository;
import com.zhu.state.infrastructure.persistence.entity.EpisodicMemory;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 把情景记忆写入 MySQL，并按身份读回最近和指定行。
 * 只在存储开关打开时创建。不改已有行。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlEpisodicMemoryRepository implements EpisodicMemoryRepository {

    private static final int TENANT_LIMIT = 64;

    private static final int USER_LIMIT = 128;

    private static final int SESSION_LIMIT = 128;

    private static final int SUMMARY_LIMIT = 500;

    private final EpisodicMemoryMapper memories;

    /** 写入走 BaseMapper。校验失败不写库。 */
    public MysqlEpisodicMemoryRepository(EpisodicMemoryMapper memories) {
        this.memories = memories;
    }

    /**
     * 空白租户和会话按空串。不截断超长字段。
     * 摘要必须是短句，不要把整段对话传进来。
     * 自增 id 给向量主键。驱动没回主键时返回 0，不要猜编号。
     */
    @Override
    public long append(String tenantId, String userId, String sessionId, String summary, int salience) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)
                || (sessionId != null && sessionId.length() > SESSION_LIMIT)
                || summary == null || summary.isBlank() || summary.length() > SUMMARY_LIMIT
                || salience < 1 || salience > 5) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "episodic memory is invalid");
        }
        EpisodicMemory row = new EpisodicMemory();
        row.setTenantId(tenantId == null || tenantId.isBlank() ? "" : tenantId);
        row.setUserId(userId);
        row.setSessionId(sessionId == null || sessionId.isBlank() ? "" : sessionId);
        row.setSummary(summary);
        row.setSalience(salience);
        memories.insert(row);
        return row.getId() == null ? 0L : row.getId();
    }

    /**
     * 空白租户按空串。条数小于 1 时按 1，大于 20 时按 20。
     */
    @Override
    public List<EpisodicMemoryItem> recent(String tenantId, String userId, int limit) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)) {
            return List.of();
        }
        int size = limit < 1 ? 1 : Math.min(limit, 20);
        return items(memories.recent(tenantId == null || tenantId.isBlank() ? "" : tenantId, userId, size));
    }

    /**
     * 丢掉空 id。身份和写入时同一套空串约定。
     */
    @Override
    public List<EpisodicMemoryItem> findByIds(String tenantId, String userId, List<Long> ids) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)
                || ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> wanted = new ArrayList<>();
        for (Long id : ids) {
            if (id != null && id > 0 && !wanted.contains(id)) {
                wanted.add(id);
            }
        }
        if (wanted.isEmpty()) {
            return List.of();
        }
        return items(memories.findByIds(tenantId == null || tenantId.isBlank() ? "" : tenantId, userId, wanted));
    }

    /** 缺主键的行丢掉。重要程度空值按 1，避免排序时空指针。 */
    private static List<EpisodicMemoryItem> items(List<EpisodicMemory> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<EpisodicMemoryItem> items = new ArrayList<>(rows.size());
        for (EpisodicMemory row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            items.add(new EpisodicMemoryItem(
                    row.getId(),
                    row.getTenantId() == null ? "" : row.getTenantId(),
                    row.getUserId(),
                    row.getSessionId() == null ? "" : row.getSessionId(),
                    row.getSummary() == null ? "" : row.getSummary(),
                    row.getSalience() == null ? 1 : row.getSalience(),
                    row.getCreatedAt()));
        }
        return List.copyOf(items);
    }
}

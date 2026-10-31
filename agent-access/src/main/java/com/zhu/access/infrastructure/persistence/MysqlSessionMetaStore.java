package com.zhu.access.infrastructure.persistence;

import com.zhu.access.api.dto.SessionSummary;
import com.zhu.access.application.SessionMetaStore;
import com.zhu.access.infrastructure.persistence.entity.SessionMeta;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 把会话元数据写进 MySQL。Redis 不在这里，缓存由 SessionListService 负责。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlSessionMetaStore implements SessionMetaStore {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SessionMetaMapper sessions;

    /** MySQL 打开时由 Mapper 扫描提供实现。 */
    public MysqlSessionMetaStore(SessionMetaMapper sessions) {
        this.sessions = sessions;
    }

    /**
     * 空租户写成空串，和唯一键对齐。时间用 UTC，不带时区。
     */
    @Override
    public void touch(String tenantId, String userId, String sessionId, String channel, String title) {
        SessionMeta row = new SessionMeta();
        row.setTenantId(tenantId == null ? "" : tenantId);
        row.setUserId(userId);
        row.setSessionId(sessionId);
        row.setChannel(channel == null || channel.isBlank() ? "WEB" : channel);
        row.setTitle(title == null ? "" : title);
        row.setLastActiveAt(LocalDateTime.now(ZoneOffset.UTC));
        sessions.touch(row);
    }

    /** 只交出列表字段。没有活动时间时用空串。 */
    @Override
    public List<SessionSummary> list(String tenantId, String userId) {
        String tenant = tenantId == null ? "" : tenantId;
        List<SessionMeta> rows = sessions.listActive(tenant, userId);
        List<SessionSummary> result = new ArrayList<>(rows.size());
        for (SessionMeta row : rows) {
            String active = row.getLastActiveAt() == null ? "" : TIME.format(row.getLastActiveAt());
            String title = row.getTitle() == null ? "" : row.getTitle();
            String channel = row.getChannel() == null ? "WEB" : row.getChannel();
            result.add(new SessionSummary(row.getSessionId(), title, channel, active));
        }
        return result;
    }

    /** 只更新已有会话。运行标识为空时不写。 */
    @Override
    public void attachRun(String tenantId, String userId, String sessionId, String runId) {
        if (userId == null || userId.isBlank() || sessionId == null || sessionId.isBlank() || runId == null || runId.isBlank()) {
            return;
        }
        String tenant = tenantId == null ? "" : tenantId;
        sessions.attachRun(tenant, userId, sessionId, runId);
    }

    /** 只看别人。自己的行不算占用。 */
    @Override
    public String otherUser(String tenantId, String sessionId, String userId) {
        if (sessionId == null || sessionId.isBlank() || userId == null || userId.isBlank()) {
            return null;
        }
        return sessions.otherUser(tenantId == null ? "" : tenantId, sessionId, userId);
    }
}

package com.zhu.admin.application.event;

import com.zhu.admin.api.event.AuditEventRow;
import com.zhu.admin.api.event.AuditQuery;
import com.zhu.ops.support.QueryLimits;
import com.zhu.readmodel.entity.event.AgentEventAudit;
import com.zhu.readmodel.persistence.event.AgentEventAuditMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 从 MySQL 读审计事件。只在存储开关打开时创建。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MybatisAuditQuery implements AuditQuery {

    private final AgentEventAuditMapper events;

    public MybatisAuditQuery(AgentEventAuditMapper events) {
        this.events = events;
    }

    /** 不带过滤条件时就是「最近事件」。 */
    @Override
    public List<AuditEventRow> recentEvents(int limit) {
        return events(null, null, null, limit);
    }

    /** 条数先收口，再按 XML 里的过滤查询取行。 */
    @Override
    public List<AuditEventRow> events(String userId, String sessionId, String runId, int limit) {
        List<AgentEventAudit> rows = events.search(
                blankToNull(userId), blankToNull(sessionId), blankToNull(runId), QueryLimits.normalize(limit));
        List<AuditEventRow> result = new ArrayList<>(rows.size());
        for (AgentEventAudit row : rows) {
            result.add(new AuditEventRow(
                    row.getEventId(),
                    row.getEventType(),
                    row.getUserId(),
                    row.getSessionId(),
                    row.getRunId(),
                    row.getSource(),
                    row.getPayload(),
                    row.getCreatedAt()));
        }
        return result;
    }

    /** 空串当「没有这个条件」，避免 XML 里把 '' 当成一个要匹配的值。 */
    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

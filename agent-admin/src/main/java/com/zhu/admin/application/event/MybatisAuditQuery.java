package com.zhu.admin.application.event;

import com.zhu.admin.api.event.AuditEventRow;
import com.zhu.admin.api.event.AuditQuery;
import com.zhu.admin.application.support.AuditQueryLimits;
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

    /** 条数先收口，再按 XML 里的最近事件查询。 */
    @Override
    public List<AuditEventRow> recentEvents(int limit) {
        List<AgentEventAudit> rows = events.recent(AuditQueryLimits.normalize(limit));
        List<AuditEventRow> result = new ArrayList<>(rows.size());
        for (AgentEventAudit row : rows) {
            result.add(new AuditEventRow(
                    row.getEventId(),
                    row.getEventType(),
                    row.getUserId(),
                    row.getSessionId(),
                    row.getSource(),
                    row.getPayload(),
                    row.getCreatedAt()));
        }
        return result;
    }
}

package com.zhu.audit.application;

import com.zhu.audit.api.ApprovalRow;
import com.zhu.audit.api.AuditEventRow;
import com.zhu.audit.api.AuditQuery;
import com.zhu.audit.infrastructure.persistence.AgentEventAuditMapper;
import com.zhu.audit.infrastructure.persistence.ApprovalRecordMapper;
import com.zhu.audit.infrastructure.persistence.entity.AgentEventAudit;
import com.zhu.audit.infrastructure.persistence.entity.ApprovalRecord;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 从 MySQL 读审计和待审批。只在存储开关打开时创建，和空查询互斥。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MybatisAuditQuery implements AuditQuery {

    private final AgentEventAuditMapper events;

    private final ApprovalRecordMapper approvals;

    public MybatisAuditQuery(AgentEventAuditMapper events, ApprovalRecordMapper approvals) {
        this.events = events;
        this.approvals = approvals;
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

    /** 只取决定仍为 PENDING 的单据。 */
    @Override
    public List<ApprovalRow> pendingApprovals(int limit) {
        List<ApprovalRecord> rows = approvals.pending(AuditQueryLimits.normalize(limit));
        List<ApprovalRow> result = new ArrayList<>(rows.size());
        for (ApprovalRecord row : rows) {
            result.add(new ApprovalRow(
                    row.getApprovalId(),
                    row.getUserId(),
                    row.getSessionId(),
                    row.getToolName(),
                    row.getDecision(),
                    row.getRequestedAt(),
                    row.getApprover(),
                    row.getChannel(),
                    row.getReason(),
                    row.getDecidedAt()));
        }
        return result;
    }
}

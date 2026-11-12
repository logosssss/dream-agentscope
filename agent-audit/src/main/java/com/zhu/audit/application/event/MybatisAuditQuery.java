package com.zhu.audit.application.event;

import com.zhu.audit.application.support.AuditQueryLimits;

import com.zhu.audit.api.approval.ApprovalRow;
import com.zhu.audit.api.event.AuditEventRow;
import com.zhu.audit.api.event.AuditQuery;
import com.zhu.audit.infrastructure.persistence.event.AgentEventAuditMapper;
import com.zhu.audit.infrastructure.persistence.approval.ApprovalRecordMapper;
import com.zhu.audit.infrastructure.persistence.entity.event.AgentEventAudit;
import com.zhu.audit.infrastructure.persistence.entity.approval.ApprovalRecord;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 从 MySQL 读审计和待审批。只在存储开关打开时创建。
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
            result.add(toRow(row));
        }
        return result;
    }

    /** 只取当前租户和用户的待确认。 */
    @Override
    public List<ApprovalRow> pendingFor(String tenantId, String userId, int limit) {
        String tenant = tenantId == null ? "" : tenantId;
        String user = userId == null ? "" : userId;
        List<ApprovalRecord> rows = approvals.pendingFor(tenant, user, AuditQueryLimits.normalize(limit));
        List<ApprovalRow> result = new ArrayList<>(rows.size());
        for (ApprovalRecord row : rows) {
            result.add(toRow(row));
        }
        return result;
    }

    /**
     * 按单号读一行，再核对本租户和发起人。别人的单按空返回，避免暴露存在性。
     */
    @Override
    public ApprovalRow ownedOne(String tenantId, String userId, String approvalId) {
        if (approvalId == null || approvalId.isBlank()) {
            return null;
        }
        ApprovalRecord row = approvals.findByApprovalId(approvalId);
        if (row == null) {
            return null;
        }
        String tenant = tenantId == null ? "" : tenantId;
        String user = userId == null ? "" : userId;
        String rowTenant = row.getTenantId() == null ? "" : row.getTenantId();
        String rowUser = row.getUserId() == null ? "" : row.getUserId();
        if (!tenant.equals(rowTenant) || !user.equals(rowUser)) {
            return null;
        }
        return toRow(row);
    }

    /** 查询行转成给管理端和用户端共用的记录。 */
    private static ApprovalRow toRow(ApprovalRecord row) {
        return new ApprovalRow(
                row.getApprovalId(),
                row.getUserId(),
                row.getSessionId(),
                row.getToolName(),
                row.getDecision(),
                row.getRequestedAt(),
                row.getApprover(),
                row.getChannel(),
                row.getReason(),
                row.getDecidedAt());
    }
}

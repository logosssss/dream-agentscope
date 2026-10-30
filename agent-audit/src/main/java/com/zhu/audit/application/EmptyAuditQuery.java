package com.zhu.audit.application;

import com.zhu.audit.api.ApprovalRow;
import com.zhu.audit.api.AuditEventRow;
import com.zhu.audit.api.AuditQuery;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * MySQL 关闭时的查询。返回空列表，不把本进程内存里的记录冒充成共享数据。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "false", matchIfMissing = true)
public class EmptyAuditQuery implements AuditQuery {

    /** 没有共享库时审计列表为空。 */
    @Override
    public List<AuditEventRow> recentEvents(int limit) {
        AuditQueryLimits.normalize(limit);
        return List.of();
    }

    /** 没有共享库时待审批列表为空。 */
    @Override
    public List<ApprovalRow> pendingApprovals(int limit) {
        AuditQueryLimits.normalize(limit);
        return List.of();
    }
}

package com.zhu.admin;

import com.zhu.audit.api.approval.ApprovalDecider;
import com.zhu.audit.api.approval.ApprovalRow;
import com.zhu.audit.api.event.AuditEventRow;
import com.zhu.audit.api.event.AuditQuery;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 管理进程测试替身。不提供写入记录器，也不连数据库。
 */
@Configuration
public class TestDependencyDoubles {

    /** 查询固定为空。 */
    @Bean
    public AuditQuery auditQuery() {
        return new AuditQuery() {
            /** 测试不返回审计。 */
            @Override
            public List<AuditEventRow> recentEvents(int limit) {
                return List.of();
            }

            /** 测试不返回待审批。 */
            @Override
            public List<ApprovalRow> pendingApprovals(int limit) {
                return List.of();
            }

            /** 测试不返回当前用户的待确认。 */
            @Override
            public List<ApprovalRow> pendingFor(String tenantId, String userId, int limit) {
                return List.of();
            }

            /** 测试不返回本人审批。 */
            @Override
            public ApprovalRow ownedOne(String tenantId, String userId, String approvalId) {
                return null;
            }
        };
    }

    /** 没有共享单据。决定一律按找不到处理。 */
    @Bean
    public ApprovalDecider approvalDecider() {
        return (approvalId, decision, approver, channel, reason) -> {
            throw new BusinessException(ErrorCode.NOT_FOUND, "approval not found");
        };
    }
}

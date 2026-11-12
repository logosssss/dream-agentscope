package com.zhu.bootstrap;

import com.zhu.access.infrastructure.cache.AccessCache;
import com.zhu.audit.api.approval.ApprovalDecider;
import com.zhu.audit.api.approval.ApprovalRecorder;
import com.zhu.audit.api.approval.ApprovalRow;
import com.zhu.audit.api.event.AuditEventRow;
import com.zhu.audit.api.event.AuditQuery;
import com.zhu.audit.api.event.AuditRecorder;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 用户进程测试替身。MySQL 和 Redis 在测试里关闭，这些接口不能再落到进程内存实现。
 */
@Configuration
public class TestDependencyDoubles {

    /** 额度测试用的计数。 */
    @Bean
    public AccessCache accessCache() {
        return new FakeAccessCache();
    }

    /** 审计写入直接丢掉。 */
    @Bean
    public AuditRecorder auditRecorder() {
        return (action, actorId, detail) -> {
        };
    }

    /** 审批写入直接丢掉。 */
    @Bean
    public ApprovalRecorder approvalRecorder() {
        return (approvalId, toolId, userId, sessionId, tenantId) -> {
        };
    }

    /** 查询固定为空，不连数据库。 */
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

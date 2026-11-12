package com.zhu.audit.infrastructure.persistence.approval;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.approval.ApprovalRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * approval_record 的 Mapper。简单插入用 BaseMapper。
 * 待审批查询和决定更新在同名 XML 里。
 */
@Mapper
public interface ApprovalRecordMapper extends BaseMapper<ApprovalRecord> {

    /**
     * 只取 PENDING，按主键从新到旧。条数由调用方收口后再传入。
     */
    List<ApprovalRecord> pending(@Param("limit") int limit);

    /**
     * 只取这个租户和用户的 PENDING。条数由调用方收口后再传入。
     */
    List<ApprovalRecord> pendingFor(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("limit") int limit);

    /**
     * 按业务单号取一行。没有时返回 null。
     */
    ApprovalRecord findByApprovalId(@Param("approvalId") String approvalId);

    /**
     * 只更新仍为 PENDING 的单据。返回实际更新行数，0 表示已被别人决定或不存在。
     */
    int decidePending(
            @Param("approvalId") String approvalId,
            @Param("decision") String decision,
            @Param("approver") String approver,
            @Param("channel") String channel,
            @Param("reason") String reason,
            @Param("decidedAt") LocalDateTime decidedAt);

    /**
     * 取这个用户、会话和工具上最早的一张已批准且未执行的单据。没有时返回 null。
     */
    ApprovalRecord findApprovedUnused(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("toolName") String toolName);

    /**
     * 取这个用户、会话和工具上最早的一张仍待处理的单据。没有时返回 null。
     * 给 ASK 复用，避免同一次对话多轮推理插出多张单。
     */
    ApprovalRecord findPendingUnused(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("toolName") String toolName);

    /**
     * 写上执行时间。只更新仍未执行的已批准单据，返回实际更新行数。
     */
    int claimExecution(@Param("approvalId") String approvalId, @Param("executedAt") LocalDateTime executedAt);

    /**
     * 回写结果 JSON。已经有结果的行不覆盖。
     */
    int writeExecutionResult(@Param("approvalId") String approvalId, @Param("result") String result);
}

package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.ApprovalRecord;
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
}

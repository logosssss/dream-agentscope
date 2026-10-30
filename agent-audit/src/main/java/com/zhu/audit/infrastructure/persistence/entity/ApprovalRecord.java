package com.zhu.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * approval_record 的一行。新单只填待审批字段，批准和拒绝以后再更新。
 */
@TableName("approval_record")
public class ApprovalRecord {

    /** 自增主键。插入时留空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 审批单标识，业务侧生成。 */
    private String approvalId;

    /** 租户。没有租户时写空串。 */
    private String tenantId;

    /** 发起人。表上非空。 */
    private String userId;

    /** 会话。没有会话时写空串，满足非空列。 */
    private String sessionId;

    /** 一次运行的标识。申请时还没有。 */
    private String runId;

    /** 待审批工具名。不存工具参数。 */
    private String toolName;

    /** 动作摘要。申请时不填。 */
    private String action;

    /** PENDING、APPROVED、REJECTED 或 EXPIRED。新单固定 PENDING。 */
    private String decision;

    /** 审批人。申请时不填。 */
    private String approver;

    /** 审批通道。申请时不填。 */
    private String channel;

    /** 审批意见。申请时不填。 */
    private String reason;

    /** 申请时间。留空时用表默认值。 */
    private LocalDateTime requestedAt;

    /** 决定时间。申请时不填。 */
    private LocalDateTime decidedAt;

    /** 批准后的执行时间。申请时不填。 */
    private LocalDateTime executedAt;

    /** 执行结果 JSON。申请时不填。 */
    private String result;

    /** 入库时间。留空时用表默认值。 */
    private LocalDateTime createdAt;

    /** 自增主键。 */
    public Long getId() {
        return id;
    }

    /** 回填数据库生成的主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 审批单标识。 */
    public String getApprovalId() {
        return approvalId;
    }

    /** 写入审批单标识。 */
    public void setApprovalId(String approvalId) {
        this.approvalId = approvalId;
    }

    /** 租户。 */
    public String getTenantId() {
        return tenantId;
    }

    /** 写入租户。 */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /** 发起人。 */
    public String getUserId() {
        return userId;
    }

    /** 写入发起人。 */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /** 会话。 */
    public String getSessionId() {
        return sessionId;
    }

    /** 写入会话。 */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /** 运行标识。 */
    public String getRunId() {
        return runId;
    }

    /** 写入运行标识。 */
    public void setRunId(String runId) {
        this.runId = runId;
    }

    /** 工具名。 */
    public String getToolName() {
        return toolName;
    }

    /** 写入工具名。 */
    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    /** 动作摘要。 */
    public String getAction() {
        return action;
    }

    /** 写入动作摘要。 */
    public void setAction(String action) {
        this.action = action;
    }

    /** 审批决定。 */
    public String getDecision() {
        return decision;
    }

    /** 写入审批决定。 */
    public void setDecision(String decision) {
        this.decision = decision;
    }

    /** 审批人。 */
    public String getApprover() {
        return approver;
    }

    /** 写入审批人。 */
    public void setApprover(String approver) {
        this.approver = approver;
    }

    /** 审批通道。 */
    public String getChannel() {
        return channel;
    }

    /** 写入审批通道。 */
    public void setChannel(String channel) {
        this.channel = channel;
    }

    /** 审批意见。 */
    public String getReason() {
        return reason;
    }

    /** 写入审批意见。 */
    public void setReason(String reason) {
        this.reason = reason;
    }

    /** 申请时间。 */
    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    /** 写入申请时间。 */
    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    /** 决定时间。 */
    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    /** 写入决定时间。 */
    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    /** 执行时间。 */
    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    /** 写入执行时间。 */
    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }

    /** 执行结果。 */
    public String getResult() {
        return result;
    }

    /** 写入执行结果。 */
    public void setResult(String result) {
        this.result = result;
    }

    /** 入库时间。 */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** 写入入库时间。 */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

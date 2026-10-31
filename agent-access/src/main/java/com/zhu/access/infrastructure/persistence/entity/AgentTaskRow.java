package com.zhu.access.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * agent_task 的一行。同步任务插入时就是执行中，不写任务参数。
 */
@TableName("agent_task")
public class AgentTaskRow {

    /** 自增主键。对外用 taskId。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务标识。 */
    private String taskId;

    /** 租户。没有时是空串。 */
    private String tenantId;

    /** 用户。 */
    private String userId;

    /** 会话。没有时留空。 */
    private String sessionId;

    /** 任务类型。 */
    private String taskType;

    /** RUNNING、SUCCEEDED 或 FAILED。 */
    private String status;

    /** 失败时的异常类型。成功时留空。 */
    private String errorMessage;

    /** 开始时间。 */
    private LocalDateTime startedAt;

    /** 结束时间。插入时留空。 */
    private LocalDateTime finishedAt;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 任务标识。 */
    public String getTaskId() {
        return taskId;
    }

    /** 写入任务标识。 */
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    /** 租户。 */
    public String getTenantId() {
        return tenantId;
    }

    /** 写入租户。 */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /** 用户。 */
    public String getUserId() {
        return userId;
    }

    /** 写入用户。 */
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

    /** 任务类型。 */
    public String getTaskType() {
        return taskType;
    }

    /** 写入任务类型。 */
    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    /** 状态。 */
    public String getStatus() {
        return status;
    }

    /** 写入状态。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 异常类型。 */
    public String getErrorMessage() {
        return errorMessage;
    }

    /** 写入异常类型。 */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /** 开始时间。 */
    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    /** 写入开始时间。 */
    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    /** 结束时间。 */
    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    /** 写入结束时间。 */
    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }
}

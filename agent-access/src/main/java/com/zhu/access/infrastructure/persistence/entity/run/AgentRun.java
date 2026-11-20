package com.zhu.access.infrastructure.persistence.entity.run;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * agent_run 的一行。只放运行结果和短摘要，不放对话全文。
 */
@TableName("agent_run")
public class AgentRun {

    /** 自增主键。对外用 runId。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 这次运行的标识。同步路径用回复上的标识。 */
    private String runId;

    /** 租户。没有租户时是空串。 */
    private String tenantId;

    /** 用户。 */
    private String userId;

    /** 会话。 */
    private String sessionId;

    /** USER 或 API。 */
    private String triggerType;

    /** SUCCEEDED、FAILED 或 CANCELLED。 */
    private String status;

    /** 模型坐标。 */
    private String model;

    /** 输入摘要。 */
    private String inputSummary;

    /** 输出摘要。 */
    private String outputSummary;

    /** 失败时的异常类型。 */
    private String errorCode;

    /** 开始时间，UTC。 */
    private LocalDateTime startedAt;

    /** 结束时间，UTC。 */
    private LocalDateTime finishedAt;

    /** 耗时毫秒。 */
    private Long durationMs;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 运行标识。 */
    public String getRunId() {
        return runId;
    }

    /** 写入运行标识。 */
    public void setRunId(String runId) {
        this.runId = runId;
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

    /** 触发方式。 */
    public String getTriggerType() {
        return triggerType;
    }

    /** 写入触发方式。 */
    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    /** 结果状态。 */
    public String getStatus() {
        return status;
    }

    /** 写入结果状态。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 模型坐标。 */
    public String getModel() {
        return model;
    }

    /** 写入模型坐标。 */
    public void setModel(String model) {
        this.model = model;
    }

    /** 输入摘要。 */
    public String getInputSummary() {
        return inputSummary;
    }

    /** 写入输入摘要。 */
    public void setInputSummary(String inputSummary) {
        this.inputSummary = inputSummary;
    }

    /** 输出摘要。 */
    public String getOutputSummary() {
        return outputSummary;
    }

    /** 写入输出摘要。 */
    public void setOutputSummary(String outputSummary) {
        this.outputSummary = outputSummary;
    }

    /** 异常类型。 */
    public String getErrorCode() {
        return errorCode;
    }

    /** 写入异常类型。 */
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
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

    /** 耗时毫秒。 */
    public Long getDurationMs() {
        return durationMs;
    }

    /** 写入耗时毫秒。 */
    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }
}

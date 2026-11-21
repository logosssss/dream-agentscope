package com.zhu.readmodel.entity.schedule;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * agent_task_schedule 的一行。描述一条任务调度，以及它最近一次触发的样子。
 *
 * <p>两个进程都会碰这张表：管理进程登记与停用，用户进程按 {@code next_fire_at} 取出并发布任务。
 * 表里不放任务参数，只放「什么时候触发、触发给谁」。
 */
@TableName("agent_task_schedule")
public class AgentTaskSchedule {

    /** 自增主键。对外不返回。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 调度标识。 */
    private String scheduleId;

    /** 任务类型。BACKFILL / REPORT / BATCH。 */
    private String taskType;

    /** 租户。空串表示没有租户。 */
    private String tenantId;

    /** 归属用户。触发时发布的任务要带它，所以新登记的行必须有。 */
    private String userId;

    /** 目标 Agent。空表示用运行配置里的默认 Agent。 */
    private String agentId;

    /** cron 表达式。延迟任务为空。 */
    private String cronExpr;

    /** 延迟秒数。cron 任务为空。 */
    private Integer delaySeconds;

    /** 1 启用。停用后不再被取出。 */
    private Integer enabled;

    /** 下次触发时间。登记时算好，触发后推进。 */
    private LocalDateTime nextFireAt;

    /** 上次触发时间。没触发过为空。 */
    private LocalDateTime lastFireAt;

    /** 最近一次触发的任务状态。没触发过为空。 */
    private String lastStatus;

    /** 已触发次数。 */
    private Integer fireCount;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 调度标识。 */
    public String getScheduleId() {
        return scheduleId;
    }

    /** 写入调度标识。 */
    public void setScheduleId(String scheduleId) {
        this.scheduleId = scheduleId;
    }

    /** 任务类型。 */
    public String getTaskType() {
        return taskType;
    }

    /** 写入任务类型。 */
    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    /** 租户。 */
    public String getTenantId() {
        return tenantId;
    }

    /** 写入租户。 */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /** 归属用户。 */
    public String getUserId() {
        return userId;
    }

    /** 写入归属用户。 */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /** 目标 Agent。 */
    public String getAgentId() {
        return agentId;
    }

    /** 写入目标 Agent。 */
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    /** cron 表达式，可能为空。 */
    public String getCronExpr() {
        return cronExpr;
    }

    /** 写入 cron。空串应先换成 null。 */
    public void setCronExpr(String cronExpr) {
        this.cronExpr = cronExpr;
    }

    /** 延迟秒数，可能为空。 */
    public Integer getDelaySeconds() {
        return delaySeconds;
    }

    /** 写入延迟秒数。 */
    public void setDelaySeconds(Integer delaySeconds) {
        this.delaySeconds = delaySeconds;
    }

    /** 是否启用。 */
    public Integer getEnabled() {
        return enabled;
    }

    /** 写入启用标记。 */
    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    /** 下次触发时间。 */
    public LocalDateTime getNextFireAt() {
        return nextFireAt;
    }

    /** 写入下次触发时间。 */
    public void setNextFireAt(LocalDateTime nextFireAt) {
        this.nextFireAt = nextFireAt;
    }

    /** 上次触发时间。 */
    public LocalDateTime getLastFireAt() {
        return lastFireAt;
    }

    /** 写入上次触发时间。 */
    public void setLastFireAt(LocalDateTime lastFireAt) {
        this.lastFireAt = lastFireAt;
    }

    /** 最近一次触发的状态。 */
    public String getLastStatus() {
        return lastStatus;
    }

    /** 写入最近一次触发的状态。 */
    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }

    /** 已触发次数。 */
    public Integer getFireCount() {
        return fireCount;
    }

    /** 写入已触发次数。 */
    public void setFireCount(Integer fireCount) {
        this.fireCount = fireCount;
    }
}

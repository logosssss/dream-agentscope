package com.zhu.audit.infrastructure.persistence.entity.schedule;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * agent_task_schedule 的一行。只描述目录，不触发执行。
 */
@TableName("agent_task_schedule")
public class AgentTaskSchedule {

    /** 自增主键。对外不返回。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 调度标识。 */
    private String scheduleId;

    /** 任务类型。 */
    private String taskType;

    /** cron 表达式。延迟任务为空。 */
    private String cronExpr;

    /** 延迟秒数。cron 任务为空。 */
    private Integer delaySeconds;

    /** 1 启用。 */
    private Integer enabled;

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
}

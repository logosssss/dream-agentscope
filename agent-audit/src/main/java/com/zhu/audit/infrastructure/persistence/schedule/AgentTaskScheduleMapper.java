package com.zhu.audit.infrastructure.persistence.schedule;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.schedule.AgentTaskSchedule;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * agent_task_schedule 的访问。登记用 BaseMapper 插入，列表与停用恢复在 XML 里。不在这里触发执行。
 */
public interface AgentTaskScheduleMapper extends BaseMapper<AgentTaskSchedule> {

    /**
     * 最近登记的任务调度。调用方已经把条数收进 1 到 100。含停用。
     */
    List<AgentTaskSchedule> recent(@Param("limit") int limit);

    /**
     * 按调度标识取一行。停用也返回。
     */
    AgentTaskSchedule findByScheduleId(@Param("scheduleId") String scheduleId);

    /**
     * 只把仍启用的那一行改成停用。返回更新行数。
     */
    int disableEnabled(@Param("scheduleId") String scheduleId);

    /**
     * 只把已停用的那一行改成启用。返回更新行数。
     */
    int restoreDisabled(@Param("scheduleId") String scheduleId);
}

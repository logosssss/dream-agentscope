package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.AgentTaskSchedule;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * agent_task_schedule 的访问。登记用 BaseMapper 插入，列表在 XML 里。不在这里触发执行。
 */
public interface AgentTaskScheduleMapper extends BaseMapper<AgentTaskSchedule> {

    /**
     * 最近登记的任务调度。调用方已经把条数收进 1 到 100。含停用。
     */
    List<AgentTaskSchedule> recent(@Param("limit") int limit);
}

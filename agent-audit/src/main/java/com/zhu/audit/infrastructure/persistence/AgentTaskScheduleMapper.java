package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.AgentTaskSchedule;

/**
 * agent_task_schedule 的访问。登记用 BaseMapper 插入，不在这里触发执行。
 */
public interface AgentTaskScheduleMapper extends BaseMapper<AgentTaskSchedule> {
}

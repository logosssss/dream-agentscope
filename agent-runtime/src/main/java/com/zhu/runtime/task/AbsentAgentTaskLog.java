package com.zhu.runtime.task;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * MySQL 关闭时的任务记录。测试用，不是写失败后的降级。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "false", matchIfMissing = true)
public class AbsentAgentTaskLog implements AgentTaskLog {

    /** 不插入。 */
    @Override
    public void begin(AgentTask task) {
    }

    /** 不更新。 */
    @Override
    public void finish(String taskId, String status, String errorCode) {
    }
}

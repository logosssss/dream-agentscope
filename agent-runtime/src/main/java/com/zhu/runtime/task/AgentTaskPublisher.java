package com.zhu.runtime.task;

/**
 * 任务发布口。业务层调用它，执行内核不感知发布实现。
 */
public interface AgentTaskPublisher {

    /** 发布一条任务。当前没有同步或 MQ 实现。 */
    void publish(AgentTask task);
}

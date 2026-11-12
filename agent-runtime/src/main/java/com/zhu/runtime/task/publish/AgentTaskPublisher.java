package com.zhu.runtime.task.publish;

import com.zhu.runtime.task.AgentTask;

/**
 * 任务发布口。业务层调用它，执行内核不感知发布实现。
 */
public interface AgentTaskPublisher {

    /**
     * 发布一条任务。mode=sync 时在当前线程执行。mode=mq 由占位实现拒绝启动，不会真的发出。
     */
    void publish(AgentTask task);
}

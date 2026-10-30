package com.zhu.runtime.task;

/**
 * 任务发布口。业务层调用它，执行内核不感知发布实现。
 */
public interface AgentTaskPublisher {

    /**
     * 发布一条任务。mode=sync 时在当前线程执行；mode=mq 当前会拒绝启动。
     */
    void publish(AgentTask task);
}

package com.zhu.runtime.task;

/**
 * 任务的消费逻辑。同步发布直接调用它，以后切到消息队列也调用同一个实现。
 * 容器里只能有一个。产品域要执行真正的任务时替换这个实现，不要再注册第二个。
 */
public interface AgentTaskHandler {

    /**
     * 处理一条已经校验过的任务。不要在这里感知发布方式。
     */
    void handle(AgentTask task);
}

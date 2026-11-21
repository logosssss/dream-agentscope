package com.zhu.runtime.task.handle;

import com.zhu.runtime.task.AgentTask;

/**
 * 一种任务类型的真实消费逻辑。
 *
 * <p>分发器按 {@link #type()} 找实现：一个类型只能有一个实现，多注册会在启动时被拒。
 * 实现放在能拿到所需依赖的模块里（索引在运行层，报表与回填在接入层），
 * 这样运行内核不必知道接入层的表。
 */
public interface AgentTaskExecutor {

    /** 这个实现负责的任务类型，与 {@link AgentTask#type()} 比较。 */
    String type();

    /**
     * 执行一条已经校验过的任务。允许抛异常：发布方会把任务记成 FAILED 再把异常抛回。
     */
    void execute(AgentTask task);
}

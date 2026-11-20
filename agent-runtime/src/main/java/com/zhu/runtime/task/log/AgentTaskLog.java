package com.zhu.runtime.task.log;

import com.zhu.runtime.task.AgentTask;

/**
 * 任务状态的落库缝。运行层不关心表，MySQL 打开时由接入层实现。
 */
public interface AgentTaskLog {

    /**
     * 开始执行前插入一行。失败时调用方不要再执行任务，避免跑完却没有记录。
     */
    void begin(AgentTask task);

    /**
     * 执行结束后改状态。errorCode 只放异常类型名，没有失败时传空。
     */
    void finish(String taskId, String status, String errorCode);
}

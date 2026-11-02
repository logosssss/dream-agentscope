package com.zhu.runtime.task;

import com.zhu.audit.api.AuditRecorder;

/**
 * 历史占位消费逻辑。只记审计，不调用模型。
 * 生产请用 {@link DispatchingAgentTaskHandler}；本类留给测试直接构造。
 */
public class SyncAgentTaskHandler implements AgentTaskHandler {

    private final AuditRecorder audit;

    public SyncAgentTaskHandler(AuditRecorder audit) {
        this.audit = audit;
    }

    /**
     * 审计里只留类型和任务标识。payload 可能含用户内容，不写入 detail。
     */
    @Override
    public void handle(AgentTask task) {
        String actor = task.userId() == null ? "" : task.userId();
        audit.record("task.handle", actor, "type=" + task.type() + ",taskId=" + task.taskId());
    }
}

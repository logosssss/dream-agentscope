package com.zhu.runtime.task.handle;

import com.zhu.runtime.task.AgentTask;
import com.zhu.audit.api.event.AuditRecorder;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 唯一的任务消费入口。按 type 分发；未知类型只记审计，兼容旧占位行为。
 */
@Service
@Primary
public class DispatchingAgentTaskHandler implements AgentTaskHandler {

    private final DocumentIndexExecutor documents;

    private final AuditRecorder audit;

    public DispatchingAgentTaskHandler(DocumentIndexExecutor documents, AuditRecorder audit) {
        this.documents = documents;
        this.audit = audit;
    }

    /**
     * DOCUMENT_INDEX 走索引；其它类型仍只记审计，不调模型。
     */
    @Override
    public void handle(AgentTask task) {
        if (task != null && DocumentIndexExecutor.TYPE.equals(task.type())) {
            documents.index(task);
            return;
        }
        String actor = task == null || task.userId() == null ? "" : task.userId();
        String type = task == null || task.type() == null ? "" : task.type();
        String taskId = task == null || task.taskId() == null ? "" : task.taskId();
        audit.record("task.handle", actor, "type=" + type + ",taskId=" + taskId);
    }
}

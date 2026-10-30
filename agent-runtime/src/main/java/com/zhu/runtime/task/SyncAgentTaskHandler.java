package com.zhu.runtime.task;

import com.zhu.audit.api.AuditRecorder;
import org.springframework.stereotype.Service;

/**
 * 还没有产品任务时的消费逻辑。只记审计，不调用模型。
 * 产品域替换这个类之后，同步发布和以后的队列消费都会走到新产品逻辑。
 */
@Service
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

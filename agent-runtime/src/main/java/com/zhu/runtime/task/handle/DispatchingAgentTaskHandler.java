package com.zhu.runtime.task.handle;

import com.zhu.runtime.task.AgentTask;
import com.zhu.runtime.api.event.AuditRecorder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 唯一的任务消费入口。按 {@link AgentTaskExecutor#type()} 分发；未知类型只记审计，兼容旧占位行为。
 *
 * <p>类型到实现是启动时定下的表：同一个类型注册两次直接失败，避免「哪份实现生效」取决于扫描顺序。
 * 执行器可以住在别的模块（索引在运行层，回填/报表/批量在接入层），本类只按类型找它。
 */
@Service
@Primary
public class DispatchingAgentTaskHandler implements AgentTaskHandler {

    private final Map<String, AgentTaskExecutor> executors;

    private final AuditRecorder audit;

    public DispatchingAgentTaskHandler(List<AgentTaskExecutor> executors, AuditRecorder audit) {
        this.executors = index(executors);
        this.audit = audit;
    }

    /**
     * 有实现就走实现；没有实现的类型仍只记审计，不调模型。
     */
    @Override
    public void handle(AgentTask task) {
        AgentTaskExecutor executor = task == null || task.type() == null ? null : executors.get(task.type());
        if (executor != null) {
            executor.execute(task);
            return;
        }
        String actor = task == null || task.userId() == null ? "" : task.userId();
        String type = task == null || task.type() == null ? "" : task.type();
        String taskId = task == null || task.taskId() == null ? "" : task.taskId();
        audit.record("task.handle", actor, "type=" + type + ",taskId=" + taskId);
    }

    /** 启动时建表。类型空白或重复都算配置错误，宁可起不来也不猜。 */
    private static Map<String, AgentTaskExecutor> index(List<AgentTaskExecutor> executors) {
        Map<String, AgentTaskExecutor> byType = new HashMap<>();
        if (executors == null) {
            return byType;
        }
        for (AgentTaskExecutor executor : executors) {
            if (executor == null) {
                continue;
            }
            String type = executor.type();
            if (type == null || type.isBlank()) {
                throw new IllegalStateException("task executor type must not be blank");
            }
            AgentTaskExecutor existing = byType.putIfAbsent(type, executor);
            if (existing != null) {
                throw new IllegalStateException("duplicate task executor type: " + type);
            }
        }
        return byType;
    }
}

package com.zhu.runtime.task;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 同步发布。调用方线程里直接执行消费逻辑，失败会抛回调用方。
 * mode=mq 时这个 Bean 不创建。现在不要引入 RocketMQ 依赖。
 */
@Service
@ConditionalOnProperty(name = "agentscope.task.mode", havingValue = "sync", matchIfMissing = true)
public class SyncTaskPublisher implements AgentTaskPublisher {

    private final AgentTaskHandler handler;

    public SyncTaskPublisher(AgentTaskHandler handler) {
        this.handler = handler;
    }

    /**
     * 缺任务标识、类型或用户时拒绝，避免记一条无法追溯的审计。
     * 不在工厂和 Middleware 里调用，发布是业务层的事。
     */
    @Override
    public void publish(AgentTask task) {
        if (task == null || blank(task.taskId()) || blank(task.type()) || blank(task.userId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "taskId, type and userId are required");
        }
        handler.handle(task);
    }

    /** 空白不算有效字段。 */
    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

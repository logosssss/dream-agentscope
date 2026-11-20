package com.zhu.runtime.task.publish;

import com.zhu.runtime.task.AgentTask;
import com.zhu.runtime.task.handle.AgentTaskHandler;
import com.zhu.runtime.task.log.AgentTaskLog;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.logging.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 同步发布。调用方线程里直接执行消费逻辑，失败会抛回调用方。
 * mode=mq 时这个 Bean 不创建。现在不要引入 RocketMQ 依赖。
 */
@Service
@ConditionalOnProperty(name = "agentscope.task.mode", havingValue = "sync", matchIfMissing = true)
public class SyncTaskPublisher implements AgentTaskPublisher {

    private static final Logger log = Logger.getLogger(SyncTaskPublisher.class.getName());

    private final AgentTaskHandler handler;

    private final AgentTaskLog tasks;

    /** 记录在执行之前。记录失败时任务不跑。 */
    public SyncTaskPublisher(AgentTaskHandler handler, AgentTaskLog tasks) {
        this.handler = handler;
        this.tasks = tasks;
    }

    /**
     * 缺任务标识、类型或用户时拒绝，避免记一条无法追溯的审计。
     * 先落 RUNNING。消费失败改成 FAILED 后再把原异常抛回。结束记录失败不掩盖消费结果。
     * 不在工厂和 Middleware 里调用，发布是业务层的事。
     */
    @Override
    public void publish(AgentTask task) {
        if (task == null || blank(task.taskId()) || blank(task.type()) || blank(task.userId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "taskId, type and userId are required");
        }
        tasks.begin(task);
        try {
            handler.handle(task);
            complete(task.taskId(), "SUCCEEDED", null);
        } catch (RuntimeException ex) {
            complete(task.taskId(), "FAILED", ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** 结束记录失败只记日志。成功的任务不能因为记账失败再报失败。 */
    private void complete(String taskId, String status, String errorCode) {
        try {
            tasks.finish(taskId, status, errorCode);
        } catch (RuntimeException ex) {
            log.warning("task record failed");
        }
    }

    /** 空白不算有效字段。 */
    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

package com.zhu.access.infrastructure.persistence;

import com.zhu.access.infrastructure.persistence.entity.AgentTaskRow;
import com.zhu.runtime.task.AgentTask;
import com.zhu.runtime.task.AgentTaskLog;
import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 把同步任务的状态写入 MySQL。不保存任务参数。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlAgentTaskLog implements AgentTaskLog {

    private static final int TYPE_LIMIT = 64;

    private static final int ERROR_LIMIT = 64;

    private final AgentTaskMapper tasks;

    /** MySQL 打开时由接入层 Mapper 扫描提供实现。 */
    public MysqlAgentTaskLog(AgentTaskMapper tasks) {
        this.tasks = tasks;
    }

    /**
     * 同步路径没有排队，插入时就是 RUNNING。重复的任务标识由唯一键抛给调用方。
     */
    @Override
    public void begin(AgentTask task) {
        AgentTaskRow row = new AgentTaskRow();
        row.setTaskId(task.taskId());
        row.setTenantId(task.tenantId() == null ? "" : limit(task.tenantId(), 64));
        row.setUserId(limit(task.userId(), 128));
        row.setSessionId(task.sessionId() == null || task.sessionId().isBlank() ? null : limit(task.sessionId(), 128));
        row.setTaskType(limit(task.type(), TYPE_LIMIT));
        row.setStatus("RUNNING");
        row.setStartedAt(LocalDateTime.now());
        tasks.insert(row);
    }

    /**
     * 异常类型超长时截断。成功时错误列留空。
     */
    @Override
    public void finish(String taskId, String status, String errorCode) {
        String error = errorCode == null || errorCode.isBlank() ? null : limit(errorCode, ERROR_LIMIT);
        tasks.finish(taskId, status, error, LocalDateTime.now());
    }

    /** 按列宽截断，避免超长字段把整行写失败。 */
    private static String limit(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}

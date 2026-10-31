package com.zhu.access.infrastructure.persistence;

import com.zhu.access.infrastructure.persistence.entity.AgentRun;
import com.zhu.access.application.RunLog;
import com.zhu.common.util.Ids;
import com.zhu.runtime.config.AgentRuntimeProperties;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 把已经结束的运行写入 MySQL。不保存输入或输出全文，也不写异常原文。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlRunLog implements RunLog {

    private static final int MODEL_LIMIT = 128;

    private static final int ERROR_LIMIT = 64;

    private final AgentRunMapper runs;

    private final AgentRuntimeProperties properties;

    /** MySQL 打开时由 Mapper 扫描提供实现。模型名来自运行配置。 */
    public MysqlRunLog(AgentRunMapper runs, AgentRuntimeProperties properties) {
        this.runs = runs;
        this.properties = properties;
    }

    /**
     * 结束时间取当前 UTC。开始时间按耗时回推。标识为空时现生成一个。
     * 用户或会话为空时不写，避免违反非空约束。
     */
    @Override
    public void complete(
            String tenantId,
            String userId,
            String sessionId,
            String trigger,
            String status,
            String runId,
            String inputSummary,
            String outputSummary,
            String errorCode,
            long startedNanos) {
        if (userId == null || userId.isBlank() || sessionId == null || sessionId.isBlank()) {
            return;
        }
        long duration = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
        LocalDateTime finished = LocalDateTime.now(ZoneOffset.UTC);
        AgentRun row = new AgentRun();
        row.setRunId(runId == null || runId.isBlank() ? Ids.newId() : runId);
        row.setTenantId(tenantId == null ? "" : tenantId);
        row.setUserId(userId);
        row.setSessionId(sessionId);
        row.setTriggerType(trigger == null || trigger.isBlank() ? "USER" : trigger);
        row.setStatus(status == null || status.isBlank() ? FAILED : status);
        row.setModel(limit(properties.getModel(), MODEL_LIMIT));
        row.setInputSummary(inputSummary == null ? "" : inputSummary);
        row.setOutputSummary(outputSummary == null ? "" : outputSummary);
        row.setErrorCode(limit(errorCode, ERROR_LIMIT));
        row.setFinishedAt(finished);
        row.setStartedAt(finished.minusNanos(duration * 1_000_000L));
        row.setDurationMs(duration);
        runs.insertFinished(row);
    }

    /** 超长时截断。空白变成 null，让表里留空。 */
    private static String limit(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}

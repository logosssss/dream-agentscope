package com.zhu.access.infrastructure.persistence.run;

import com.zhu.access.application.chat.run.RunLog;
import com.zhu.access.application.chat.run.RunStep;
import com.zhu.access.infrastructure.persistence.entity.run.AgentRun;
import com.zhu.access.infrastructure.persistence.entity.run.AgentRunStep;
import com.zhu.common.util.Ids;
import com.zhu.common.exception.BusinessException;
import com.zhu.runtime.application.turn.ResolvedChatModel;
import com.zhu.runtime.config.AgentRuntimeProperties;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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

    private static final int TOOL_LIMIT = 128;

    private final AgentRunMapper runs;

    private final AgentRunStepMapper steps;

    private final AgentRuntimeProperties properties;

    private final ResolvedChatModel chatModel;

    /** MySQL 打开时由 Mapper 扫描提供实现。模型名优先用已登记配置。 */
    public MysqlRunLog(
            AgentRunMapper runs,
            AgentRunStepMapper steps,
            AgentRuntimeProperties properties,
            ResolvedChatModel chatModel) {
        this.runs = runs;
        this.steps = steps;
        this.properties = properties;
        this.chatModel = chatModel;
    }

    /**
     * 结束时间取当前 UTC。开始时间按耗时回推。标识为空时现生成一个。
     * 用户或会话为空时不写，避免违反非空约束。
     * 步骤在运行行之后写入。没有步骤时不插第二张表。
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
            List<RunStep> recorded,
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
        row.setModel(limit(modelName(tenantId), MODEL_LIMIT));
        row.setInputSummary(inputSummary == null ? "" : inputSummary);
        row.setOutputSummary(outputSummary == null ? "" : outputSummary);
        row.setErrorCode(limit(errorCode, ERROR_LIMIT));
        row.setFinishedAt(finished);
        row.setStartedAt(finished.minusNanos(duration * 1_000_000L));
        row.setDurationMs(duration);
        runs.insertFinished(row);
        List<AgentRunStep> rows = numbered(recorded);
        if (!rows.isEmpty()) {
            steps.insertSteps(row.getRunId(), rows);
        }
    }

    /**
     * 记运行失败不改变已经结束的对话。
     * 选模型失败时退回进程配置，避免丢掉这一行。
     */
    private String modelName(String tenantId) {
        try {
            return chatModel.resolve(tenantId);
        } catch (BusinessException ex) {
            return properties.getModel();
        }
    }

    /** 丢掉没有类型的步骤。序号按留下的顺序从 1 递增。工具名超长则截断。 */
    private static List<AgentRunStep> numbered(List<RunStep> recorded) {
        if (recorded == null || recorded.isEmpty()) {
            return List.of();
        }
        List<AgentRunStep> rows = new ArrayList<>();
        int number = 1;
        for (RunStep step : recorded) {
            if (step == null || step.stepType() == null || step.stepType().isBlank()) {
                continue;
            }
            AgentRunStep row = new AgentRunStep();
            row.setStepNo(number++);
            row.setStepType(step.stepType());
            row.setToolId(limit(step.toolId(), TOOL_LIMIT));
            row.setStatus(step.status() == null || step.status().isBlank() ? SUCCEEDED : step.status());
            rows.add(row);
        }
        return rows;
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

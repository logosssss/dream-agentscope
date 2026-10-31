package com.zhu.access.application;

import com.zhu.access.api.dto.ChatRequest;
import com.zhu.audit.api.TenantGate;
import com.zhu.access.domain.channel.AccessChannel;
import com.zhu.common.context.RequestIdContext;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.util.Ids;
import com.zhu.semantic.api.PlatformPrompt;
import com.zhu.semantic.api.PromptService;
import com.zhu.semantic.contract.ProductSemanticProvider;
import com.zhu.runtime.api.AgentExecutor;
import com.zhu.runtime.api.AgentRequest;
import com.zhu.runtime.api.AgentResponse;
import com.zhu.runtime.api.AgentStreamEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

/**
 * 把接入层请求译成 AgentRequest。这里不接触 HarnessAgent。
 */
@Service
public class ChatAppService {

    private static final Logger log = Logger.getLogger(ChatAppService.class.getName());

    private final AgentExecutor executor;

    private final PromptService prompts;

    private final ProductSemanticProvider semantics;

    private final QuotaGuard quota;

    private final SessionTurnGuard turns;

    private final SessionIndex sessions;

    private final RunLog runs;

    private final TenantGate tenants;

    /**
     * 租户检查在额度和占锁之前。MySQL 关闭时检查放行。
     */
    public ChatAppService(
            AgentExecutor executor,
            PromptService prompts,
            ProductSemanticProvider semantics,
            QuotaGuard quota,
            SessionTurnGuard turns,
            SessionIndex sessions,
            RunLog runs,
            TenantGate tenants) {
        this.executor = executor;
        this.prompts = prompts;
        this.semantics = semantics;
        this.quota = quota;
        this.turns = turns;
        this.sessions = sessions;
        this.runs = runs;
        this.tenants = tenants;
    }

    /**
     * 同步执行一次对话。
     * 请求体里的租户和用户优先；空字段才用过滤器放进线程的请求头。通道留在接入层，不放进 AgentRequest。
     */
    public AgentResponse chat(ChatRequest request) {
        AgentRequest agentRequest = toRequest(request);
        tenants.requireActive(agentRequest.tenantId());
        quota.check(agentRequest.tenantId(), agentRequest.userId(), "chat", RequestIdContext.get());
        SessionTurnGuard.Turn turn = turns.open(agentRequest.tenantId(), agentRequest.userId(), agentRequest.sessionId());
        long started = System.nanoTime();
        try {
            remember(request, agentRequest);
            AgentResponse response = executor.execute(agentRequest);
            finish(request, agentRequest, RunLog.SUCCEEDED, response.runId(), response.text(), null, started, reasoning(RunLog.SUCCEEDED));
            return response;
        } catch (RuntimeException error) {
            finish(request, agentRequest, RunLog.FAILED, Ids.newId(), "", error.getClass().getSimpleName(), started, reasoning(RunLog.FAILED));
            throw error;
        } finally {
            turns.close(turn);
        }
    }

    /**
     * 流式执行。身份在返回 Flux 之前就抄进 AgentRequest，避免过滤器清掉线程上下文后流才开始读。
     * 会话锁在流结束时释放。调用方不要 block。
     */
    public Flux<AgentStreamEvent> stream(ChatRequest request) {
        AgentRequest agentRequest = toRequest(request);
        tenants.requireActive(agentRequest.tenantId());
        quota.check(agentRequest.tenantId(), agentRequest.userId(), "chat-stream", RequestIdContext.get());
        SessionTurnGuard.Turn turn = turns.open(agentRequest.tenantId(), agentRequest.userId(), agentRequest.sessionId());
        long started = System.nanoTime();
        try {
            remember(request, agentRequest);
            String runId = Ids.newId();
            AtomicReference<String> output = new AtomicReference<>("");
            AtomicReference<String> errorCode = new AtomicReference<>();
            ConcurrentLinkedQueue<AgentStreamEvent> seen = new ConcurrentLinkedQueue<>();
            return executor.stream(agentRequest)
                    .doOnNext(event -> keep(seen, output, event))
                    .doOnError(error -> errorCode.set(error.getClass().getSimpleName()))
                    .doFinally(signal -> {
                        turns.close(turn);
                        String status = streamStatus(signal);
                        String code = RunLog.FAILED.equals(status) ? errorCode.get() : null;
                        finish(request, agentRequest, status, runId, output.get(), code, started, stepsFrom(seen, status));
                    });
        } catch (RuntimeException error) {
            turns.close(turn);
            finish(request, agentRequest, RunLog.FAILED, Ids.newId(), "", error.getClass().getSimpleName(), started, reasoning(RunLog.FAILED));
            throw error;
        }
    }

    /**
     * 额度通过并占锁之后才记会话。记失败会释放锁并让这次对话失败。
     * 通道留在列表里，不放进 AgentRequest。
     */
    private void remember(ChatRequest request, AgentRequest agentRequest) {
        sessions.record(
                agentRequest.tenantId(),
                agentRequest.userId(),
                agentRequest.sessionId(),
                request.channelOrDefault().name(),
                request.text());
    }

    /**
     * 运行记录写失败只打固定日志，不改变回复，也不把异常原文打出来。
     * 摘要沿用列表标题的长度，换行压成空格。步骤只带类型和工具名。
     */
    private void finish(
            ChatRequest request,
            AgentRequest agentRequest,
            String status,
            String runId,
            String output,
            String errorCode,
            long startedNanos,
            List<RunStep> steps) {
        try {
            String id = runId == null || runId.isBlank() ? Ids.newId() : runId;
            runs.complete(
                    agentRequest.tenantId(),
                    agentRequest.userId(),
                    agentRequest.sessionId(),
                    trigger(request),
                    status,
                    id,
                    SessionListService.title(request.text()),
                    SessionListService.title(output),
                    errorCode,
                    steps,
                    startedNanos);
            sessions.attachRun(agentRequest.tenantId(), agentRequest.userId(), agentRequest.sessionId(), id);
        } catch (RuntimeException error) {
            log.warning("run record failed");
        }
    }

    /** 增量文本不留下。工具开始和待确认才进入步骤。结果文本只留给运行摘要。 */
    private static void keep(ConcurrentLinkedQueue<AgentStreamEvent> seen, AtomicReference<String> output, AgentStreamEvent event) {
        if (event == null || event.type() == null) {
            return;
        }
        if ("agent_result".equals(event.type())) {
            output.set(event.text());
            return;
        }
        if ("tool_call_start".equals(event.type()) || "require_user_confirm".equals(event.type())) {
            seen.add(event);
        }
    }

    /**
     * 工具和待确认按看到的顺序编号。最后补一步推理。
     * 工具步骤只表示调用已经开始，不表示工具成功。
     */
    private static List<RunStep> stepsFrom(ConcurrentLinkedQueue<AgentStreamEvent> seen, String runStatus) {
        List<RunStep> steps = new ArrayList<>();
        for (AgentStreamEvent event : seen) {
            if ("tool_call_start".equals(event.type())) {
                addNamed(steps, RunStep.TOOL, event.text());
            } else if ("require_user_confirm".equals(event.type())) {
                addNamed(steps, RunStep.APPROVAL, event.text());
            }
        }
        steps.add(reasoning(runStatus).get(0));
        return List.copyOf(steps);
    }

    /** 逗号分开的多个工具名各记一步。空白名字跳过。 */
    private static void addNamed(List<RunStep> steps, String type, String names) {
        if (names == null || names.isBlank()) {
            return;
        }
        for (String name : names.split(",")) {
            String toolId = name.trim();
            if (toolId.isEmpty()) {
                continue;
            }
            if (toolId.length() > 128) {
                toolId = toolId.substring(0, 128);
            }
            steps.add(new RunStep(type, toolId, RunLog.SUCCEEDED));
        }
    }

    /** 取消没有推理结果，记成跳过。失败和成功沿用运行状态。 */
    private static List<RunStep> reasoning(String runStatus) {
        String status = RunLog.CANCELLED.equals(runStatus) ? RunStep.SKIPPED : runStatus;
        if (!RunLog.FAILED.equals(status) && !RunStep.SKIPPED.equals(status)) {
            status = RunLog.SUCCEEDED;
        }
        return List.of(new RunStep(RunStep.REASONING, null, status));
    }

    /** 取消单独记。其它非正常结束都算失败。 */
    private static String streamStatus(SignalType signal) {
        if (signal == SignalType.ON_COMPLETE) {
            return RunLog.SUCCEEDED;
        }
        if (signal == SignalType.CANCEL) {
            return RunLog.CANCELLED;
        }
        return RunLog.FAILED;
    }

    /** 程序调用记成 API。页面和即时通讯仍是用户触发。 */
    private static String trigger(ChatRequest request) {
        if (request.channelOrDefault() == AccessChannel.API) {
            return "API";
        }
        return "USER";
    }

    /** 通道只在接入层归一化，不传给运行层。 */
    private AgentRequest toRequest(ChatRequest request) {
        request.channelOrDefault();
        return new AgentRequest(
                request.agentId(),
                firstNonBlank(request.tenantId(), TenantContext.get()),
                firstNonBlank(request.userId(), UserContext.get()),
                request.sessionId(),
                systemPrompt(request),
                request.text());
    }

    /**
     * 请求体里的提示词优先。空则按键读取，读不到用平台兜底，避免空串进入模型。
     */
    private String systemPrompt(ChatRequest request) {
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            return request.systemPrompt();
        }
        return prompts.get(PlatformPrompt.SYSTEM_KEY, Map.of(), semantics.promptFallback(PlatformPrompt.SYSTEM_KEY));
    }

    /** 空白不算有效身份，否则空字符串会挡住请求头。 */
    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback == null || fallback.isBlank()) {
            return null;
        }
        return fallback;
    }
}

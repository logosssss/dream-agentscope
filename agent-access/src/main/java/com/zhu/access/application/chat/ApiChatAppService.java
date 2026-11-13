package com.zhu.access.application.chat;

import com.zhu.access.api.dto.chat.ChatRequest;
import com.zhu.access.application.account.QuotaGuard;
import com.zhu.access.application.chat.gateway.AccessChannelGateway;
import com.zhu.access.application.chat.message.ChatMessageStore;
import com.zhu.access.application.chat.run.ConcurrentRunGuard;
import com.zhu.access.application.chat.run.RunLog;
import com.zhu.access.application.chat.run.RunStep;
import com.zhu.access.application.chat.session.SessionIndex;
import com.zhu.access.application.chat.session.SessionListService;
import com.zhu.access.application.chat.session.SessionOwnerGate;
import com.zhu.access.domain.channel.AccessChannel;
import com.zhu.audit.api.tenant.TenantGate;
import com.zhu.audit.api.tenant.TenantUserGate;
import com.zhu.common.context.RequestIdContext;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.util.Ids;
import com.zhu.runtime.api.AgentRequest;
import com.zhu.runtime.api.AgentResponse;
import com.zhu.runtime.api.AgentStreamEvent;
import com.zhu.semantic.api.prompt.PlatformPrompt;
import com.zhu.semantic.api.prompt.PromptService;
import com.zhu.semantic.contract.product.ProductSemanticProvider;
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
 * API Channel 对话应用服务。护栏与额度同 {@link ChatAppService}，执行走 {@link AccessChannelGateway#executeApi}。
 * 见 docs/api-channel-落地.md。
 */
@Service
public class ApiChatAppService {

    private static final Logger log = Logger.getLogger(ApiChatAppService.class.getName());

    private final AccessChannelGateway gateway;

    private final PromptService prompts;

    private final ProductSemanticProvider semantics;

    private final QuotaGuard quota;

    private final ConcurrentRunGuard concurrent;

    private final SessionIndex sessions;

    private final RunLog runs;

    private final TenantGate tenants;

    private final TenantUserGate tenantUsers;

    private final SessionOwnerGate owners;

    private final ChatMessageStore messages;

    /**
     * 依赖与 WEB 对话服务对称；执行入口换成 Gateway API Channel。
     */
    public ApiChatAppService(
            AccessChannelGateway gateway,
            PromptService prompts,
            ProductSemanticProvider semantics,
            QuotaGuard quota,
            ConcurrentRunGuard concurrent,
            SessionIndex sessions,
            RunLog runs,
            TenantGate tenants,
            TenantUserGate tenantUsers,
            SessionOwnerGate owners,
            ChatMessageStore messages) {
        this.gateway = gateway;
        this.prompts = prompts;
        this.semantics = semantics;
        this.quota = quota;
        this.concurrent = concurrent;
        this.sessions = sessions;
        this.runs = runs;
        this.tenants = tenants;
        this.tenantUsers = tenantUsers;
        this.owners = owners;
        this.messages = messages;
    }

    /**
     * 同步 API 对话。身份只来自登录会话；通道固定记为 API。
     */
    public AgentResponse chat(ChatRequest request) {
        ChatRequest apiRequest = forceApi(request);
        AgentRequest agentRequest = toRequest(apiRequest);
        requireUser(agentRequest.userId());
        tenants.requireActive(agentRequest.tenantId());
        tenantUsers.requireActive(agentRequest.tenantId(), agentRequest.userId());
        owners.requireExclusive(agentRequest.tenantId(), agentRequest.userId(), agentRequest.sessionId());
        quota.check(agentRequest.tenantId(), agentRequest.userId(), "chat", RequestIdContext.get());
        ConcurrentRunGuard.Slot slot = concurrent.open(agentRequest.tenantId());
        long started = System.nanoTime();
        try {
            remember(apiRequest, agentRequest);
            AgentResponse response = gateway.executeApi(agentRequest);
            finish(apiRequest, agentRequest, RunLog.SUCCEEDED, response.runId(), response.text(), null, started,
                    reasoning(RunLog.SUCCEEDED));
            return response;
        } catch (RuntimeException error) {
            finish(apiRequest, agentRequest, RunLog.FAILED, Ids.newId(), "", error.getClass().getSimpleName(), started,
                    reasoning(RunLog.FAILED));
            throw error;
        } finally {
            concurrent.close(slot);
        }
    }

    /**
     * 流式 API 对话。额度动作码仍为 chat-stream。
     */
    public Flux<AgentStreamEvent> stream(ChatRequest request) {
        ChatRequest apiRequest = forceApi(request);
        AgentRequest agentRequest = toRequest(apiRequest);
        requireUser(agentRequest.userId());
        tenants.requireActive(agentRequest.tenantId());
        tenantUsers.requireActive(agentRequest.tenantId(), agentRequest.userId());
        owners.requireExclusive(agentRequest.tenantId(), agentRequest.userId(), agentRequest.sessionId());
        quota.check(agentRequest.tenantId(), agentRequest.userId(), "chat-stream", RequestIdContext.get());
        ConcurrentRunGuard.Slot slot = concurrent.open(agentRequest.tenantId());
        long started = System.nanoTime();
        try {
            remember(apiRequest, agentRequest);
            String runId = Ids.newId();
            AtomicReference<String> output = new AtomicReference<>("");
            AtomicReference<String> errorCode = new AtomicReference<>();
            ConcurrentLinkedQueue<AgentStreamEvent> seen = new ConcurrentLinkedQueue<>();
            ConcurrentRunGuard.Slot held = slot;
            return gateway.streamApi(agentRequest)
                    .doOnNext(event -> keep(seen, output, event))
                    .doOnError(error -> errorCode.set(error.getClass().getSimpleName()))
                    .doFinally(signal -> {
                        try {
                            String status = streamStatus(signal);
                            String code = RunLog.FAILED.equals(status) ? errorCode.get() : null;
                            finish(apiRequest, agentRequest, status, runId, output.get(), code, started,
                                    stepsFrom(seen, status));
                        } finally {
                            concurrent.close(held);
                        }
                    });
        } catch (RuntimeException error) {
            try {
                finish(apiRequest, agentRequest, RunLog.FAILED, Ids.newId(), "", error.getClass().getSimpleName(),
                        started, reasoning(RunLog.FAILED));
            } finally {
                concurrent.close(slot);
            }
            throw error;
        }
    }

    /** 无论请求体写什么 channel，本服务一律按 API 记账。 */
    private static ChatRequest forceApi(ChatRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "request is required");
        }
        return new ChatRequest(
                request.agentId(),
                request.tenantId(),
                request.userId(),
                request.sessionId(),
                request.systemPrompt(),
                request.text(),
                AccessChannel.API);
    }

    private void remember(ChatRequest request, AgentRequest agentRequest) {
        sessions.record(
                agentRequest.tenantId(),
                agentRequest.userId(),
                agentRequest.sessionId(),
                AccessChannel.API.name(),
                request.text());
    }

    private void finish(
            ChatRequest request,
            AgentRequest agentRequest,
            String status,
            String runId,
            String output,
            String errorCode,
            long startedNanos,
            List<RunStep> steps) {
        String id = runId == null || runId.isBlank() ? Ids.newId() : runId;
        try {
            runs.complete(
                    agentRequest.tenantId(),
                    agentRequest.userId(),
                    agentRequest.sessionId(),
                    "API",
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
        if (!RunLog.SUCCEEDED.equals(status)) {
            return;
        }
        try {
            messages.appendTurn(
                    agentRequest.tenantId(),
                    agentRequest.userId(),
                    agentRequest.sessionId(),
                    id,
                    request.text() == null ? "" : request.text(),
                    output == null ? "" : output);
        } catch (RuntimeException error) {
            log.warning("chat message record failed");
        }
    }

    private static void keep(
            ConcurrentLinkedQueue<AgentStreamEvent> seen, AtomicReference<String> output, AgentStreamEvent event) {
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

    private static List<RunStep> reasoning(String runStatus) {
        String status = RunLog.CANCELLED.equals(runStatus) ? RunStep.SKIPPED : runStatus;
        if (!RunLog.FAILED.equals(status) && !RunStep.SKIPPED.equals(status)) {
            status = RunLog.SUCCEEDED;
        }
        return List.of(new RunStep(RunStep.REASONING, null, status));
    }

    private static String streamStatus(SignalType signal) {
        if (signal == SignalType.ON_COMPLETE) {
            return RunLog.SUCCEEDED;
        }
        if (signal == SignalType.CANCEL) {
            return RunLog.CANCELLED;
        }
        return RunLog.FAILED;
    }

    private AgentRequest toRequest(ChatRequest request) {
        String tenantId = TenantContext.get() == null ? "" : TenantContext.get();
        return new AgentRequest(
                request.agentId(),
                tenantId,
                UserContext.get(),
                request.sessionId(),
                prompts.get(PlatformPrompt.SYSTEM_KEY, Map.of(), semantics.promptFallback(PlatformPrompt.SYSTEM_KEY)),
                request.text());
    }

    private static void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "login is required");
        }
    }
}

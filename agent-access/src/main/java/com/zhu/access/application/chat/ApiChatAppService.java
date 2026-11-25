package com.zhu.access.application.chat;

import com.zhu.access.api.dto.chat.ChatRequest;
import com.zhu.access.application.account.QuotaGuard;
import com.zhu.access.application.chat.message.ChatMessageStore;
import com.zhu.access.application.chat.run.ConcurrentRunGuard;
import com.zhu.access.application.chat.run.RunLog;
import com.zhu.access.application.chat.run.RunStep;
import com.zhu.access.application.chat.run.StreamRunSteps;
import com.zhu.access.application.chat.session.SessionIndex;
import com.zhu.access.application.chat.session.SessionListService;
import com.zhu.access.application.chat.session.SessionOwnerGate;
import com.zhu.access.domain.channel.AccessChannel;
import com.zhu.ops.tenant.TenantGate;
import com.zhu.ops.tenant.TenantUserGate;
import com.zhu.common.context.RequestIdContext;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.util.Ids;
import com.zhu.docs.api.DocsAgent;
import com.zhu.runtime.api.agent.AgentExecutor;
import com.zhu.runtime.api.agent.AgentRequest;
import com.zhu.runtime.api.agent.AgentResponse;
import com.zhu.runtime.api.agent.AgentStreamEvent;
import com.zhu.semantic.api.prompt.PlatformPrompt;
import com.zhu.semantic.api.prompt.PromptService;
import com.zhu.semantic.contract.product.ProductSemanticProvider;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * API Channel 对话应用服务。护栏与额度同 {@link ChatAppService}，执行走 {@link AgentExecutor#executeApi}。
 * 见 docs/api-channel-落地.md。
 */
@Service
public class ApiChatAppService {

    private static final Logger log = Logger.getLogger(ApiChatAppService.class.getName());

    private final AgentExecutor gateway;

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
            AgentExecutor gateway,
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
        ConcurrentGate.Slot slot = GateChain.enter(tenants, tenantUsers, owners, quota, concurrent, agentRequest, "chat");
        long started = System.nanoTime();
        // API 通道同样按请求号对齐运行记录；流式回调读不到线程上下文，先在入口抄走。
        String requestId = RequestIdContext.get();
        try {
            remember(apiRequest, agentRequest);
            AgentResponse response = gateway.executeApi(agentRequest);
            finish(apiRequest, agentRequest, RunLog.SUCCEEDED, response.runId(), response.text(), null, requestId, started,
                    StreamRunSteps.reasoningOnly(RunLog.SUCCEEDED));
            return response;
        } catch (RuntimeException error) {
            finish(apiRequest, agentRequest, RunLog.FAILED, Ids.newId(), "", error.getClass().getSimpleName(), requestId, started,
                    StreamRunSteps.reasoningOnly(RunLog.FAILED));
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
        ConcurrentGate.Slot slot = GateChain.enter(tenants, tenantUsers, owners, quota, concurrent, agentRequest, "chat-stream");
        long started = System.nanoTime();
        String requestId = RequestIdContext.get();
        try {
            remember(apiRequest, agentRequest);
            String runId = Ids.newId();
            AtomicReference<String> output = new AtomicReference<>("");
            AtomicReference<String> errorCode = new AtomicReference<>();
            ConcurrentLinkedQueue<AgentStreamEvent> seen = new ConcurrentLinkedQueue<>();
            ConcurrentGate.Slot held = slot;
            return gateway.streamApi(agentRequest)
                    .doOnNext(event -> StreamRunSteps.observe(seen, output, event))
                    .doOnError(error -> errorCode.set(error.getClass().getSimpleName()))
                    .doFinally(signal -> {
                        try {
                            String status = StreamRunSteps.statusOf(signal);
                            String code = RunLog.FAILED.equals(status) ? errorCode.get() : null;
                            finish(apiRequest, agentRequest, status, runId, output.get(), code, requestId, started,
                                    StreamRunSteps.from(seen, status));
                        } finally {
                            concurrent.close(held);
                        }
                    });
        } catch (RuntimeException error) {
            try {
                finish(apiRequest, agentRequest, RunLog.FAILED, Ids.newId(), "", error.getClass().getSimpleName(),
                        requestId, started, StreamRunSteps.reasoningOnly(RunLog.FAILED));
            } finally {
                concurrent.close(slot);
            }
            throw error;
        }
    }

    /**
     * 无论请求体写什么 channel，本服务一律按 API 记账。
     * API Channel 本期不做随消息附件。
     */
    private static ChatRequest forceApi(ChatRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "request is required");
        }
        // 文档助手只从 /api/docs/doc/{id}/ask 进入。这里放行的话，通用对话会带上工具和记忆。
        DocsAgent.rejectPublicChat(request.agentId());
        if (!request.attachmentIdsOrEmpty().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "attachments are not supported on API channel");
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
            String requestId,
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
                    requestId,
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
}

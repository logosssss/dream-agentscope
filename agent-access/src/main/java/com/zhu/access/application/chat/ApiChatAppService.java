package com.zhu.access.application.chat;

import com.zhu.access.api.dto.chat.ChatRequest;
import com.zhu.access.application.account.QuotaGuard;
import com.zhu.access.application.chat.message.ChatMessageStore;
import com.zhu.access.application.chat.run.ConcurrentRunGuard;
import com.zhu.access.application.chat.run.RunLog;
import com.zhu.access.application.chat.run.RunStep;
import com.zhu.access.application.chat.session.SessionIndex;
import com.zhu.access.application.chat.session.SessionOwnerGate;
import com.zhu.access.domain.channel.AccessChannel;
import com.zhu.access.infrastructure.security.ClientIp;
import com.zhu.ops.tenant.TenantGate;
import com.zhu.ops.tenant.TenantUserGate;
import com.zhu.common.context.UserContext;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.docs.api.DocsAgent;
import com.zhu.runtime.api.agent.AgentExecutor;
import com.zhu.runtime.api.agent.AgentRequest;
import com.zhu.runtime.api.agent.AgentResponse;
import com.zhu.runtime.api.agent.AgentStreamEvent;
import com.zhu.semantic.api.prompt.PromptService;
import com.zhu.semantic.contract.product.ProductSemanticProvider;
import java.util.List;
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
        String clientIp = ClientIp.capture();
        ChatRequest apiRequest = forceApi(request);
        AgentRequest agentRequest = toRequest(apiRequest);
        return GatedChat.sync(
                tenants,
                tenantUsers,
                owners,
                quota,
                concurrent,
                agentRequest,
                () -> remember(apiRequest, agentRequest),
                () -> gateway.executeApi(agentRequest),
                (status, runId, output, errorCode, requestId, started, steps) -> finish(
                        apiRequest, agentRequest, status, runId, output, errorCode, requestId, clientIp, started, steps));
    }

    /**
     * 流式 API 对话。来源地址在返回 Flux 之前抄走。额度动作码仍为 chat-stream。
     */
    public Flux<AgentStreamEvent> stream(ChatRequest request) {
        String clientIp = ClientIp.capture();
        ChatRequest apiRequest = forceApi(request);
        AgentRequest agentRequest = toRequest(apiRequest);
        return GatedChat.stream(
                tenants,
                tenantUsers,
                owners,
                quota,
                concurrent,
                agentRequest,
                () -> remember(apiRequest, agentRequest),
                () -> gateway.streamApi(agentRequest),
                (status, runId, output, errorCode, requestId, started, steps) -> finish(
                        apiRequest, agentRequest, status, runId, output, errorCode, requestId, clientIp, started, steps));
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

    /**
     * 额度通过后记会话。通道固定 API，标题用请求正文。
     */
    private void remember(ChatRequest request, AgentRequest agentRequest) {
        SessionTurn.remember(sessions, agentRequest, AccessChannel.API.name(), request.text());
    }

    /**
     * 结束落库。触发来源固定 API，不挂附件。标题用请求正文。
     */
    private void finish(
            ChatRequest request,
            AgentRequest agentRequest,
            String status,
            String runId,
            String output,
            String errorCode,
            String requestId,
            String clientIp,
            long startedNanos,
            List<RunStep> steps) {
        SessionTurn.finish(
                log,
                runs,
                sessions,
                messages,
                agentRequest,
                "API",
                status,
                runId,
                requestId,
                clientIp,
                request.text(),
                output,
                errorCode,
                steps,
                startedNanos,
                null);
    }

    private AgentRequest toRequest(ChatRequest request) {
        return new AgentRequest(
                request.agentId(),
                AccessCaller.tenantId(),
                UserContext.get(),
                request.sessionId(),
                AccessCaller.systemPrompt(prompts, semantics),
                request.text());
    }
}

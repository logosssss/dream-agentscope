package com.zhu.access.application;

import com.zhu.access.api.dto.ChatRequest;
import com.zhu.common.context.RequestIdContext;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.semantic.api.PlatformPrompt;
import com.zhu.semantic.api.PromptService;
import com.zhu.semantic.contract.ProductSemanticProvider;
import com.zhu.runtime.api.AgentExecutor;
import com.zhu.runtime.api.AgentRequest;
import com.zhu.runtime.api.AgentResponse;
import com.zhu.runtime.api.AgentStreamEvent;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 把接入层请求译成 AgentRequest。这里不接触 HarnessAgent。
 */
@Service
public class ChatAppService {

    private final AgentExecutor executor;

    private final PromptService prompts;

    private final ProductSemanticProvider semantics;

    private final QuotaGuard quota;

    private final SessionTurnGuard turns;

    private final SessionIndex sessions;

    public ChatAppService(
            AgentExecutor executor,
            PromptService prompts,
            ProductSemanticProvider semantics,
            QuotaGuard quota,
            SessionTurnGuard turns,
            SessionIndex sessions) {
        this.executor = executor;
        this.prompts = prompts;
        this.semantics = semantics;
        this.quota = quota;
        this.turns = turns;
        this.sessions = sessions;
    }

    /**
     * 同步执行一次对话。
     * 请求体里的租户和用户优先；空字段才用过滤器放进线程的请求头。通道留在接入层，不放进 AgentRequest。
     */
    public AgentResponse chat(ChatRequest request) {
        AgentRequest agentRequest = toRequest(request);
        quota.check(agentRequest.tenantId(), agentRequest.userId(), "chat", RequestIdContext.get());
        SessionTurnGuard.Turn turn = turns.open(agentRequest.tenantId(), agentRequest.userId(), agentRequest.sessionId());
        try {
            remember(request, agentRequest);
            return executor.execute(agentRequest);
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
        quota.check(agentRequest.tenantId(), agentRequest.userId(), "chat-stream", RequestIdContext.get());
        SessionTurnGuard.Turn turn = turns.open(agentRequest.tenantId(), agentRequest.userId(), agentRequest.sessionId());
        try {
            remember(request, agentRequest);
            return executor.stream(agentRequest).doFinally(signal -> turns.close(turn));
        } catch (RuntimeException error) {
            turns.close(turn);
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

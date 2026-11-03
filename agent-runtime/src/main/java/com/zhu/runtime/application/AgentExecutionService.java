package com.zhu.runtime.application;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.util.Ids;
import com.zhu.runtime.api.AgentExecutor;
import com.zhu.runtime.api.AgentRequest;
import com.zhu.runtime.api.AgentResponse;
import com.zhu.runtime.api.AgentStreamEvent;
import com.zhu.runtime.api.UsageMeter;
import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.runtime.domain.CrisisReplyGuard;
import com.zhu.runtime.domain.ModelTextGuard;
import com.zhu.runtime.domain.PersonaConsistencyGuard;
import com.zhu.runtime.domain.middleware.PersonaConsistencyMiddleware;
import com.zhu.runtime.infrastructure.agent.HarnessAgentFactory;
import com.zhu.semantic.contract.CrisisPrompt;
import com.zhu.semantic.contract.PersonaContract;
import com.zhu.semantic.contract.RiskLevel;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 单次执行的生命周期。身份放进 RuntimeContext，执行完无论成败都关闭 Agent。
 * 危机档流式仍攒齐再出；普通人设检查允许增量打出，终态再覆盖。
 */
@Service
public class AgentExecutionService implements AgentExecutor {

    private final HarnessAgentFactory factory;

    private final AgentRuntimeProperties properties;

    private final ResolvedChatModel chatModel;

    private final UsageMeter usages;

    private final CrisisPrompt crisis;

    private final PersonaContract persona;

    public AgentExecutionService(
            HarnessAgentFactory factory,
            AgentRuntimeProperties properties,
            ResolvedChatModel chatModel,
            UsageMeter usages,
            CrisisPrompt crisis,
            PersonaContract persona) {
        this.factory = factory;
        this.properties = properties;
        this.chatModel = chatModel;
        this.usages = usages;
        this.crisis = crisis;
        this.persona = persona;
    }

    /**
     * 新建 Agent、同步等待回复、然后关闭。不要把返回的 Agent 留到方法外面。
     * 危机档和打开人设检查时等整段回来再过，避免半句先漏出去。
     */
    @Override
    public AgentResponse execute(AgentRequest request) {
        require(request);
        RuntimeContext context = context(request);
        try (HarnessAgent agent = factory.build(request)) {
            long started = System.nanoTime();
            Msg reply = agent.call(request.userText(), context)
                    .block(Duration.ofSeconds(properties.getTimeoutSeconds()));
            String text = finish(reply == null ? null : reply.getTextContent(), context);
            String runId = reply == null || reply.getId() == null ? Ids.newId() : reply.getId();
            ModelCallUsage.record(
                    usages,
                    request.tenantId(),
                    request.userId(),
                    request.sessionId(),
                    runId,
                    chatModel.resolve(request.tenantId()),
                    reply,
                    started);
            return new AgentResponse(runId, text);
        }
    }

    /**
     * 先建好 Agent 和上下文再返回 Flux，这样请求线程上的身份已经被抄进 RuntimeContext。
     * 订阅结束时关闭 Agent。超时只作用于流，不在请求线程上 block。
     * 危机档仍扣增量；普通人设检查放行增量，结束事件再带检查后的全文。
     */
    @Override
    public Flux<AgentStreamEvent> stream(AgentRequest request) {
        require(request);
        HarnessAgent agent = factory.build(request);
        RuntimeContext context = context(request);
        long started = System.nanoTime();
        try {
            return agent.streamEvents(request.userText(), context)
                    .doOnNext(event -> {
                        if (event instanceof AgentResultEvent result) {
                            Msg message = result.getResult();
                            String runId = message == null || message.getId() == null ? Ids.newId() : message.getId();
                            ModelCallUsage.record(
                                    usages,
                                    request.tenantId(),
                                    request.userId(),
                                    request.sessionId(),
                                    runId,
                                    chatModel.resolve(request.tenantId()),
                                    message,
                                    started);
                        }
                    })
                    .concatMapIterable(event -> mapStreamEvent(event, context))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .doFinally(signal -> close(agent));
        } catch (RuntimeException error) {
            close(agent);
            throw error;
        }
    }

    /** 档位由安全门写入同一份上下文。没有档位不当成危机。 */
    private static boolean critical(RuntimeContext context) {
        return context != null && context.get(RiskLevel.class) == RiskLevel.CRITICAL;
    }

    /** 人设标记由中间件写入同一份上下文。危机不再查人设。 */
    private static boolean personaCheck(RuntimeContext context) {
        return context != null && context.get(PersonaConsistencyMiddleware.Flag.class) == PersonaConsistencyMiddleware.Flag.ON;
    }

    /**
     * 流式事件映射：危机仍扣增量，结束再出护栏全文。
     * 普通人设检查不再扣增量，否则前端看不到打字效果；终态仍走 finish 覆盖。
     */
    private List<AgentStreamEvent> mapStreamEvent(AgentEvent event, RuntimeContext context) {
        if (critical(context)) {
            return CrisisReplyOutlet.holdThenFinish(event, true, text -> finish(text, context));
        }
        if (event instanceof AgentResultEvent) {
            AgentStreamEvent mapped = AgentStreamEvents.from(event);
            return List.of(new AgentStreamEvent(mapped.type(), finish(mapped.text(), context)));
        }
        return List.of(AgentStreamEvents.from(event));
    }

    /**
     * 先过危机红线。危机命中后不再拿预置稿去查人设。
     * 人设基线读不到时用代码兜底。
     */
    private String finish(String raw, RuntimeContext context) {
        String text = CrisisReplyGuard.finalize(raw, critical(context), crisisText());
        if (critical(context)) {
            return text;
        }
        return PersonaConsistencyGuard.finalize(text, personaCheck(context), PersonaConsistencyGuard.baseline(persona));
    }

    /** 读失败或空白用代码兜底。盖掉回复时不再回头检查这篇稿。 */
    private String crisisText() {
        try {
            String text = crisis == null ? null : crisis.text();
            if (text == null || text.isBlank()) {
                return CrisisPrompt.FALLBACK;
            }
            return text;
        } catch (RuntimeException ex) {
            return CrisisPrompt.FALLBACK;
        }
    }

    /** 身份放进框架上下文。tenantId 和 agentId 不是框架字段，放在扩展项里。 */
    private RuntimeContext context(AgentRequest request) {
        return RuntimeContext.builder()
                .userId(request.userId())
                .sessionId(request.sessionId())
                .put("tenantId", request.tenantId())
                .put("agentId", agentId(request))
                .build();
    }

    /** 请求没带标识时用运行配置。空配置仍退回 companion-v1，避免向量缺 Agent。 */
    private String agentId(AgentRequest request) {
        if (request != null && !blank(request.agentId())) {
            return request.agentId();
        }
        if (!blank(properties.getAgentId())) {
            return properties.getAgentId();
        }
        return "companion-v1";
    }

    /** 关闭失败不能盖住已经产生的回复或原始错误。 */
    private static void close(HarnessAgent agent) {
        try {
            agent.close();
        } catch (RuntimeException ignored) {
            // 关闭是收尾，不再向外抛。
        }
    }

    /** 用户、会话和输入缺一不可。过长的输入和未替换的系统提示词不能进模型。 */
    private void require(AgentRequest request) {
        if (request == null
                || blank(request.userId())
                || blank(request.sessionId())
                || blank(request.userText())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId, sessionId and userText are required");
        }
        ModelTextGuard.beforeCall(request.systemPrompt(), request.userText(), properties.getMaxUserChars());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

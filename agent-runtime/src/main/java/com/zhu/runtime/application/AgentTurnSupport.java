package com.zhu.runtime.application;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.util.Ids;
import com.zhu.runtime.api.AgentRequest;
import com.zhu.runtime.api.AgentStreamEvent;
import com.zhu.runtime.api.UsageMeter;
import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.runtime.domain.CrisisReplyGuard;
import com.zhu.runtime.domain.ModelTextGuard;
import com.zhu.runtime.domain.PersonaConsistencyGuard;
import com.zhu.runtime.domain.middleware.PersonaConsistencyMiddleware;
import com.zhu.semantic.contract.safety.CrisisPrompt;
import com.zhu.semantic.contract.product.PersonaContract;
import com.zhu.semantic.contract.safety.RiskLevel;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.Msg;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 运行层零件：校验、RuntimeContext、流式映射、危机/人设收口与用量记账。
 * 单次执行和 Channel 长驻路径共用，避免两套护栏。
 */
@Component
public class AgentTurnSupport {

    private final AgentRuntimeProperties properties;

    private final ResolvedChatModel chatModel;

    private final UsageMeter usages;

    private final CrisisPrompt crisis;

    private final PersonaContract persona;

    /** 注入运行配置与护栏依赖。 */
    public AgentTurnSupport(
            AgentRuntimeProperties properties,
            ResolvedChatModel chatModel,
            UsageMeter usages,
            CrisisPrompt crisis,
            PersonaContract persona) {
        this.properties = properties;
        this.chatModel = chatModel;
        this.usages = usages;
        this.crisis = crisis;
        this.persona = persona;
    }

    /** 用户、会话和输入缺一不可。过长的输入和未替换的系统提示词不能进模型。 */
    public void require(AgentRequest request) {
        if (request == null
                || blank(request.userId())
                || blank(request.sessionId())
                || blank(request.userText())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId, sessionId and userText are required");
        }
        ModelTextGuard.beforeCall(request.systemPrompt(), request.userText(), properties.getMaxUserChars());
    }

    /** 身份放进框架上下文。tenantId 和 agentId 不是框架字段，放在扩展项里。 */
    public RuntimeContext context(AgentRequest request) {
        return RuntimeContext.builder()
                .userId(request.userId())
                .sessionId(request.sessionId())
                .put("tenantId", request.tenantId())
                .put("agentId", agentId(request))
                .build();
    }

    /** 请求没带标识时用运行配置。空配置仍退回 companion-v1。 */
    public String agentId(AgentRequest request) {
        if (request != null && !blank(request.agentId())) {
            return request.agentId();
        }
        if (!blank(properties.getAgentId())) {
            return properties.getAgentId();
        }
        return "companion-v1";
    }

    /** 同步或流式结束后记账。 */
    public void recordUsage(AgentRequest request, String runId, Msg reply, long startedNanos) {
        ModelCallUsage.record(
                usages,
                request.tenantId(),
                request.userId(),
                request.sessionId(),
                runId,
                chatModel.resolve(request.tenantId()),
                reply,
                startedNanos);
    }

    /**
     * 流式事件映射：危机仍扣增量，结束再出护栏全文。
     * 普通人设检查不再扣增量；终态仍走 finish 覆盖。
     */
    public List<AgentStreamEvent> mapStreamEvent(AgentEvent event, RuntimeContext context) {
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
     */
    public String finish(String raw, RuntimeContext context) {
        String text = CrisisReplyGuard.finalize(raw, critical(context), crisisText());
        if (critical(context)) {
            return text;
        }
        return PersonaConsistencyGuard.finalize(text, personaCheck(context), PersonaConsistencyGuard.baseline(persona));
    }

    /** 从结果消息取运行标识；没有则新生成。 */
    public static String runIdOf(Msg reply) {
        if (reply == null || reply.getId() == null || reply.getId().isBlank()) {
            return Ids.newId();
        }
        return reply.getId();
    }

    /** 调用超时秒数。 */
    public int timeoutSeconds() {
        return properties.getTimeoutSeconds();
    }

    private static boolean critical(RuntimeContext context) {
        return context != null && context.get(RiskLevel.class) == RiskLevel.CRITICAL;
    }

    private static boolean personaCheck(RuntimeContext context) {
        return context != null
                && context.get(PersonaConsistencyMiddleware.Flag.class) == PersonaConsistencyMiddleware.Flag.ON;
    }

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

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

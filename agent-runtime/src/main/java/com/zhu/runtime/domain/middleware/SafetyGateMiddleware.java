package com.zhu.runtime.domain.middleware;

import com.zhu.runtime.domain.KeywordRiskClassifier;
import com.zhu.semantic.contract.CrisisPrompt;
import com.zhu.semantic.contract.RiskLevel;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 安全门。关键词命中危机时换危机话术，不再用搭子人设。
 * 挂在审批之后。语义判定和干预落库还不在这里。
 */
@Component
public class SafetyGateMiddleware implements MiddlewareBase {

    private final CrisisPrompt crisis;

    public SafetyGateMiddleware(CrisisPrompt crisis) {
        this.crisis = crisis;
    }

    /**
     * 先看这次用户原文，再把档位放进上下文。系统提示词变换发生在后续链路里。
     * 上下文为空时只往下走，不打断对话。
     */
    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext context,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        RiskLevel level = KeywordRiskClassifier.classify(userText(input));
        if (context != null) {
            context.put(RiskLevel.class, level);
        }
        return next.apply(input);
    }

    /**
     * 危机档换成 classpath 话术。读失败仍用代码兜底，不能把空串或搭子人设交给模型。
     * 其它档原样留下，升高标记以后再加。
     */
    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext context, String currentPrompt) {
        if (!critical(context)) {
            return Mono.just(currentPrompt == null ? "" : currentPrompt);
        }
        try {
            String text = crisis == null ? null : crisis.text();
            if (text == null || text.isBlank()) {
                return Mono.just(CrisisPrompt.FALLBACK);
            }
            return Mono.just(text);
        } catch (RuntimeException ex) {
            return Mono.just(CrisisPrompt.FALLBACK);
        }
    }

    /** 从后往前找用户消息。历史里的助手回复不参与这次判定。 */
    private static String userText(AgentInput input) {
        if (input == null || input.msgs() == null) {
            return "";
        }
        List<Msg> messages = input.msgs();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Msg message = messages.get(i);
            if (message == null || message.getRole() != MsgRole.USER) {
                continue;
            }
            String text = message.getTextContent();
            return text == null ? "" : text;
        }
        return "";
    }

    /** 没有上下文或没有档位时不当成危机，避免误换话术。 */
    private static boolean critical(RuntimeContext context) {
        if (context == null) {
            return false;
        }
        return context.get(RiskLevel.class) == RiskLevel.CRITICAL;
    }
}

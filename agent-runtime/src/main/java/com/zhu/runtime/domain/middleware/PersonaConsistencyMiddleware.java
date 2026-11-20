package com.zhu.runtime.domain.middleware;

import com.zhu.runtime.api.session.SessionTurnCount;
import com.zhu.runtime.domain.CallIdentity;
import com.zhu.semantic.contract.safety.RiskLevel;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 人设一致性。长对话或用户纠正身份时打开检查标记。
 * 规则检查在执行层做。这里不调模型，也不写干预。
 * 挂在安全门之后，危机档不打开标记。标记只活这一轮。
 * 情景/画像表已硬删：不再用「跨会话恢复」读表触发；只留纠正短句 + 满 20 轮。
 * 跨会话记忆权威是 {@code MEMORY.md}。
 */
@Component
public class PersonaConsistencyMiddleware implements MiddlewareBase {

    private static final Logger log = Logger.getLogger(PersonaConsistencyMiddleware.class.getName());

    /** 已经成功这么多轮后，下一轮是第 21 轮。 */
    public static final int TURN_THRESHOLD = 20;

    /**
     * 用户对准身份的纠正。只认这几句，不把「更喜欢」这类闲聊算进去。
     */
    static final List<String> CORRECTIONS = List.of(
            "你不是我的恋人",
            "你不是助手",
            "不要叫我亲爱的",
            "别把我当对象");

    /**
     * 上下文标记。有值表示这一轮要查人设。
     */
    public enum Flag {
        ON
    }

    private final SessionTurnCount turns;

    /** 计数口可空。空或读失败不当成触发。 */
    public PersonaConsistencyMiddleware(SessionTurnCount turns) {
        this.turns = turns;
    }

    /**
     * 先看档位和触发条件，再往下走。
     * 危机档保持关闭，避免盖掉危机话术。
     */
    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext context,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        if (context != null && !critical(context) && ready(context, input)) {
            context.put(Flag.class, Flag.ON);
        }
        return next.apply(input);
    }

    /**
     * 纠正身份或满 20 轮才打开。轮次读失败时轮次条件不开，纠正仍可凭原文打开。
     */
    private boolean ready(RuntimeContext context, AgentInput input) {
        if (corrected(userText(input))) {
            return true;
        }
        if (turns == null) {
            return false;
        }
        CallIdentity identity = CallIdentity.from(context);
        try {
            int succeeded = turns.succeeded(identity.tenantId(), identity.userId(), identity.sessionId());
            return succeeded >= TURN_THRESHOLD;
        } catch (RuntimeException ex) {
            log.warning("session turn count failed");
            return false;
        }
    }

    /** 按包含匹配。空原文不算纠正。 */
    static boolean corrected(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String phrase : CORRECTIONS) {
            if (normalized.contains(phrase.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
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

    /** 没有档位不当成危机。 */
    private static boolean critical(RuntimeContext context) {
        return context != null && context.get(RiskLevel.class) == RiskLevel.CRITICAL;
    }
}

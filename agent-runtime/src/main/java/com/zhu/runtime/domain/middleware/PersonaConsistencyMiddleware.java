package com.zhu.runtime.domain.middleware;

import com.zhu.runtime.api.SessionTurnCount;
import com.zhu.runtime.domain.CallIdentity;
import com.zhu.semantic.contract.safety.RiskLevel;
import com.zhu.state.api.memory.EpisodicMemoryItem;
import com.zhu.state.api.memory.EpisodicMemoryRepository;
import com.zhu.state.api.memory.UserProfileItem;
import com.zhu.state.api.memory.UserProfileRepository;
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
 * 人设一致性。长对话、跨会话恢复或用户纠正身份时打开检查标记。
 * 规则检查在执行层做。这里不调模型，也不写干预。
 * 挂在安全门之后，危机档不打开标记。标记只活这一轮。
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

    private final UserProfileRepository profiles;

    private final EpisodicMemoryRepository memories;

    /** 计数口或记忆口可空。空或读失败都不当成触发。 */
    public PersonaConsistencyMiddleware(
            SessionTurnCount turns,
            UserProfileRepository profiles,
            EpisodicMemoryRepository memories) {
        this.turns = turns;
        this.profiles = profiles;
        this.memories = memories;
    }

    /**
     * 先看档位和触发条件，再往下走。读库失败不当成触发。
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
     * 纠正身份、满 20 轮，或本会话第 1 轮且已有跨会话记忆，才打开。
     * 轮次读失败时这两条都不开，纠正仍可凭原文打开。
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
            if (succeeded >= TURN_THRESHOLD) {
                return true;
            }
            return succeeded == 0 && remembered(identity);
        } catch (RuntimeException ex) {
            log.warning("session turn count failed");
            return false;
        }
    }

    /**
     * 画像或最近一条情景记忆任一存在就算跨会话恢复。
     * 某一边读失败只跳过那一边，两边都没有或都失败则不开。
     */
    private boolean remembered(CallIdentity identity) {
        if (profiles != null) {
            try {
                UserProfileItem profile = profiles.find(identity.tenantId(), identity.userId());
                if (profile != null) {
                    return true;
                }
            } catch (RuntimeException ex) {
                log.warning("persona profile lookup failed");
            }
        }
        if (memories == null) {
            return false;
        }
        try {
            List<EpisodicMemoryItem> rows = memories.recent(identity.tenantId(), identity.userId(), 1);
            return rows != null && !rows.isEmpty();
        } catch (RuntimeException ex) {
            log.warning("persona memory lookup failed");
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

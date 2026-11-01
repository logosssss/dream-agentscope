package com.zhu.runtime.domain.middleware;

import com.zhu.runtime.api.SessionTurnCount;
import com.zhu.runtime.domain.CallIdentity;
import com.zhu.semantic.contract.RiskLevel;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import java.util.function.Function;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 人设一致性。只在会话已经成功超过 20 轮时打开检查标记。
 * 规则检查在执行层做。这里不调模型，也不写干预。
 * 挂在安全门之后，危机档不打开标记。
 */
@Component
public class PersonaConsistencyMiddleware implements MiddlewareBase {

    private static final Logger log = Logger.getLogger(PersonaConsistencyMiddleware.class.getName());

    /** 已经成功这么多轮后，下一轮是第 21 轮。 */
    public static final int TURN_THRESHOLD = 20;

    /**
     * 上下文标记。有值表示这一轮要查人设。
     */
    public enum Flag {
        ON
    }

    private final SessionTurnCount turns;

    /** 计数口可空。空或读失败都不当成已经满 20 轮。 */
    public PersonaConsistencyMiddleware(SessionTurnCount turns) {
        this.turns = turns;
    }

    /**
     * 先看档位和成功轮次，再往下走。读轮次失败当 0，不打开检查。
     * 危机档保持关闭，避免盖掉危机话术。
     */
    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext context,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        if (context != null && !critical(context) && ready(context)) {
            context.put(Flag.class, Flag.ON);
        }
        return next.apply(input);
    }

    /** 已经成功 20 轮后这一轮才查。没有计数口按 0。 */
    private boolean ready(RuntimeContext context) {
        if (turns == null) {
            return false;
        }
        CallIdentity identity = CallIdentity.from(context);
        try {
            return turns.succeeded(identity.tenantId(), identity.userId(), identity.sessionId()) >= TURN_THRESHOLD;
        } catch (RuntimeException ex) {
            log.warning("session turn count failed");
            return false;
        }
    }

    /** 没有档位不当成危机。 */
    private static boolean critical(RuntimeContext context) {
        return context != null && context.get(RiskLevel.class) == RiskLevel.CRITICAL;
    }
}

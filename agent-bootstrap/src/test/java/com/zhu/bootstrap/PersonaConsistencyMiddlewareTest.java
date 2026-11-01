package com.zhu.bootstrap;

import com.zhu.runtime.domain.middleware.PersonaConsistencyMiddleware;
import com.zhu.semantic.contract.RiskLevel;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 人设检查只在成功满 20 轮后打开。危机档不打开。
 */
class PersonaConsistencyMiddlewareTest {

    /** 19 轮仍关闭。20 轮打开。 */
    @Test
    void enablesAfterTwentySucceeded() {
        RuntimeContext early = run(19, RiskLevel.NORMAL);
        assertThat(early.get(PersonaConsistencyMiddleware.Flag.class)).isNull();

        RuntimeContext ready = run(20, RiskLevel.NORMAL);
        assertThat(ready.get(PersonaConsistencyMiddleware.Flag.class)).isEqualTo(PersonaConsistencyMiddleware.Flag.ON);
    }

    /** 危机档即使轮次够了也不打开，避免盖掉危机话术。 */
    @Test
    void criticalDoesNotEnable() {
        RuntimeContext context = run(30, RiskLevel.CRITICAL);
        assertThat(context.get(PersonaConsistencyMiddleware.Flag.class)).isNull();
    }

    /** 计数失败当 0，不打开检查。 */
    @Test
    void countFailureKeepsCheckOff() {
        PersonaConsistencyMiddleware middleware = new PersonaConsistencyMiddleware((tenant, user, session) -> {
            throw new IllegalStateException("db");
        });
        RuntimeContext context = context(RiskLevel.NORMAL);
        middleware.onAgent(mock(Agent.class), context, input(), ignored -> Flux.empty()).blockLast();
        assertThat(context.get(PersonaConsistencyMiddleware.Flag.class)).isNull();
    }

    /** 计数口看到同一份身份。 */
    @Test
    void countUsesCallIdentity() {
        AtomicInteger seen = new AtomicInteger();
        PersonaConsistencyMiddleware middleware = new PersonaConsistencyMiddleware((tenant, user, session) -> {
            assertThat(tenant).isEqualTo("t");
            assertThat(user).isEqualTo("u");
            assertThat(session).isEqualTo("s");
            seen.incrementAndGet();
            return 20;
        });
        middleware.onAgent(mock(Agent.class), context(RiskLevel.NORMAL), input(), ignored -> Flux.empty()).blockLast();
        assertThat(seen).hasValue(1);
    }

    private static RuntimeContext run(int succeeded, RiskLevel level) {
        PersonaConsistencyMiddleware middleware = new PersonaConsistencyMiddleware((tenant, user, session) -> succeeded);
        RuntimeContext context = context(level);
        middleware.onAgent(mock(Agent.class), context, input(), ignored -> Flux.empty()).blockLast();
        return context;
    }

    private static RuntimeContext context(RiskLevel level) {
        return RuntimeContext.builder()
                .userId("u")
                .sessionId("s")
                .put("tenantId", "t")
                .put(RiskLevel.class, level)
                .build();
    }

    private static AgentInput input() {
        return new AgentInput(List.of(Msg.builder().role(MsgRole.USER).textContent("hi").build()));
    }
}

package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.zhu.runtime.domain.middleware.PersonaConsistencyMiddleware;
import com.zhu.semantic.contract.safety.RiskLevel;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.middleware.AgentInput;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * 人设检查在满 20 轮或纠正身份时打开。危机档不打开。
 * 情景/画像表已硬删：不再测「跨会话恢复」读表触发。
 */
class PersonaConsistencyMiddlewareTest {

    /** 19 轮仍关闭。20 轮打开。 */
    @Test
    void enablesAfterTwentySucceeded() {
        RuntimeContext early = run(19, RiskLevel.NORMAL, "hi");
        assertThat(early.get(PersonaConsistencyMiddleware.Flag.class)).isNull();

        RuntimeContext ready = run(20, RiskLevel.NORMAL, "hi");
        assertThat(ready.get(PersonaConsistencyMiddleware.Flag.class)).isEqualTo(PersonaConsistencyMiddleware.Flag.ON);
    }

    /** 第一轮闲聊保持关闭。 */
    @Test
    void firstTurnWithoutCorrectionStaysOff() {
        RuntimeContext context = run(0, RiskLevel.NORMAL, "我更喜欢周末出门");
        assertThat(context.get(PersonaConsistencyMiddleware.Flag.class)).isNull();
    }

    /** 对准身份的纠正这一轮就打开，不看轮次。 */
    @Test
    void enablesOnIdentityCorrection() {
        RuntimeContext context = run(0, RiskLevel.NORMAL, "你不是助手");
        assertThat(context.get(PersonaConsistencyMiddleware.Flag.class)).isEqualTo(PersonaConsistencyMiddleware.Flag.ON);
    }

    /** 危机档即使纠正或轮次够了也不打开，避免盖掉危机话术。 */
    @Test
    void criticalDoesNotEnable() {
        assertThat(run(30, RiskLevel.CRITICAL, "你不是助手").get(PersonaConsistencyMiddleware.Flag.class)).isNull();
        assertThat(run(0, RiskLevel.CRITICAL, "hi").get(PersonaConsistencyMiddleware.Flag.class)).isNull();
    }

    /** 计数失败当没触发，不打开检查。 */
    @Test
    void countFailureKeepsCheckOff() {
        PersonaConsistencyMiddleware middleware = new PersonaConsistencyMiddleware(
                (tenant, user, session) -> {
                    throw new IllegalStateException("db");
                });
        RuntimeContext context = context(RiskLevel.NORMAL);
        middleware.onAgent(mock(Agent.class), context, input("hi"), ignored -> Flux.empty()).blockLast();
        assertThat(context.get(PersonaConsistencyMiddleware.Flag.class)).isNull();
    }

    /** 计数口看到同一份身份。 */
    @Test
    void countUsesCallIdentity() {
        AtomicInteger seen = new AtomicInteger();
        PersonaConsistencyMiddleware middleware = new PersonaConsistencyMiddleware(
                (tenant, user, session) -> {
                    assertThat(tenant).isEqualTo("t");
                    assertThat(user).isEqualTo("u");
                    assertThat(session).isEqualTo("s");
                    seen.incrementAndGet();
                    return 20;
                });
        middleware.onAgent(mock(Agent.class), context(RiskLevel.NORMAL), input("hi"), ignored -> Flux.empty()).blockLast();
        assertThat(seen).hasValue(1);
    }

    private static RuntimeContext run(int succeeded, RiskLevel level, String userText) {
        PersonaConsistencyMiddleware middleware = new PersonaConsistencyMiddleware(
                (tenant, user, session) -> succeeded);
        RuntimeContext context = context(level);
        middleware.onAgent(mock(Agent.class), context, input(userText), ignored -> Flux.empty()).blockLast();
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

    private static AgentInput input(String userText) {
        return new AgentInput(List.of(Msg.builder().role(MsgRole.USER).textContent(userText).build()));
    }
}

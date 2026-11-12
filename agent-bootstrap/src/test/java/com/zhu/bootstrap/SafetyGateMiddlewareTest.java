package com.zhu.bootstrap;

import com.zhu.audit.api.safety.SafetyInterventionRecorder;
import com.zhu.runtime.domain.middleware.SafetyGateMiddleware;
import com.zhu.semantic.contract.safety.CrisisPrompt;
import com.zhu.semantic.contract.safety.RiskLevel;
import com.zhu.semantic.contract.safety.SemanticRiskJudge;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.middleware.AgentInput;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 关键词危机换话术。假语义判定只抬档，不连模型。
 */
class SafetyGateMiddlewareTest {

    /** 危机词改系统提示词。闲聊保持原话术。 */
    @Test
    void criticalReplacesSystemPrompt() {
        AtomicInteger judged = new AtomicInteger();
        List<String> recorded = new ArrayList<>();
        SafetyGateMiddleware middleware = gate(
                () -> "危机话术",
                text -> {
                    judged.incrementAndGet();
                    return RiskLevel.NORMAL;
                },
                recorded);

        assertThat(promptOf(middleware, "我想自杀", "搭子人设")).isEqualTo("危机话术");
        assertThat(judged).hasValue(0);
        assertThat(recorded).containsExactly("CRITICAL KEYWORD keyword critical");
        assertThat(recorded.get(0)).doesNotContain("自杀");

        recorded.clear();
        assertThat(promptOf(middleware, "今天天气不错", "搭子人设")).isEqualTo("搭子人设");
        assertThat(recorded).isEmpty();
    }

    /** 假语义判定返回危机时换危机话术。 */
    @Test
    void semanticCriticalReplacesPrompt() {
        List<String> recorded = new ArrayList<>();
        SafetyGateMiddleware middleware = gate(() -> "危机话术", text -> RiskLevel.CRITICAL, recorded);

        assertThat(promptOf(middleware, "听起来不太对", "搭子人设")).isEqualTo("危机话术");
        assertThat(recorded).containsExactly("CRITICAL SEMANTIC semantic critical");
        assertThat(recorded.get(0)).doesNotContain("不太对");
    }

    /** 假语义判定返回升高时不换危机模板，只加克制标记。 */
    @Test
    void elevatedKeepsCompanionPrompt() {
        List<String> recorded = new ArrayList<>();
        SafetyGateMiddleware middleware = gate(() -> "危机话术", text -> RiskLevel.ELEVATED, recorded);

        String prompt = promptOf(middleware, "我有点难受", "搭子人设");
        assertThat(prompt).startsWith("搭子人设");
        assertThat(prompt).contains(SafetyGateMiddleware.ELEVATED_MARK);
        assertThat(prompt).isNotEqualTo("危机话术");
        assertThat(recorded).containsExactly("ELEVATED SEMANTIC semantic elevated");
        assertThat(recorded.get(0)).doesNotContain("难受");
    }

    /** 语义判定抛错按升高，对话不失败，也不换危机话术。 */
    @Test
    void judgeFailureIsElevated() {
        List<String> recorded = new ArrayList<>();
        SafetyGateMiddleware middleware = gate(() -> "危机话术", text -> {
            throw new IllegalStateException("down");
        }, recorded);

        String prompt = promptOf(middleware, "随便说一句", "搭子人设");
        assertThat(prompt).contains("搭子人设");
        assertThat(prompt).contains(SafetyGateMiddleware.ELEVATED_MARK);
        assertThat(prompt).isNotEqualTo("危机话术");
        assertThat(recorded).containsExactly("ELEVATED JUDGE_FAILED judge failed");
    }

    /** 危机正文读失败时用代码兜底，不能退回搭子人设。 */
    @Test
    void crisisReadFailureUsesFallback() {
        SafetyGateMiddleware middleware = gate(() -> {
            throw new IllegalStateException("missing");
        }, text -> RiskLevel.NORMAL, new ArrayList<>());

        assertThat(promptOf(middleware, "我想自杀", "搭子人设")).isEqualTo(CrisisPrompt.FALLBACK);
    }

    /** 写干预失败不改变已经选出的话术。 */
    @Test
    void recordFailureDoesNotChangePrompt() {
        SafetyGateMiddleware middleware = new SafetyGateMiddleware(
                () -> "危机话术",
                text -> RiskLevel.ELEVATED,
                (tenant, user, session, level, source, summary) -> {
                    throw new IllegalStateException("db");
                });

        assertThat(promptOf(middleware, "我有点难受", "搭子人设")).contains(SafetyGateMiddleware.ELEVATED_MARK);
    }

    /** 先判定再换话术。和框架里 onAgent 包住 onSystemPrompt 的顺序一致。 */
    private static String promptOf(SafetyGateMiddleware middleware, String userText, String current) {
        RuntimeContext context = RuntimeContext.builder()
                .userId("u")
                .sessionId("s")
                .put("tenantId", "t")
                .build();
        AgentInput input = new AgentInput(List.of(
                Msg.builder().role(MsgRole.USER).textContent(userText).build()));
        middleware.onAgent(mock(Agent.class), context, input, ignored -> Flux.empty()).blockLast();
        return middleware.onSystemPrompt(mock(Agent.class), context, current).block();
    }

    /** 测试里的判定器和记录器都不连网。 */
    private static SafetyGateMiddleware gate(
            CrisisPrompt crisis,
            SemanticRiskJudge judge,
            List<String> recorded) {
        SafetyInterventionRecorder interventions =
                (tenant, user, session, level, source, summary) ->
                        recorded.add(level + " " + source + " " + summary);
        return new SafetyGateMiddleware(crisis, judge, interventions);
    }
}

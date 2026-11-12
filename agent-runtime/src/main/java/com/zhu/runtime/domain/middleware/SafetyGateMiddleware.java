package com.zhu.runtime.domain.middleware;

import com.zhu.audit.api.safety.SafetyInterventionRecorder;
import com.zhu.runtime.domain.CallIdentity;
import com.zhu.runtime.domain.KeywordRiskClassifier;
import com.zhu.semantic.contract.safety.CrisisPrompt;
import com.zhu.semantic.contract.safety.RiskLevel;
import com.zhu.semantic.contract.safety.SemanticRiskJudge;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 安全门。关键词或语义判定到危机时换危机话术，升高时只加克制标记。
 * 挂在审批之后。干预写失败不打断这次对话。输出红线不在这里查。
 */
@Component
public class SafetyGateMiddleware implements MiddlewareBase {

    private static final Logger log = Logger.getLogger(SafetyGateMiddleware.class.getName());

    /** 升高时追加到搭子人设后面。不换危机模板。 */
    public static final String ELEVATED_MARK = "当前需要更克制。不要开玩笑，不要讨论危险方法，不要替用户做决定。";

    private final CrisisPrompt crisis;

    private final SemanticRiskJudge judge;

    private final SafetyInterventionRecorder interventions;

    public SafetyGateMiddleware(
            CrisisPrompt crisis,
            SemanticRiskJudge judge,
            SafetyInterventionRecorder interventions) {
        this.crisis = crisis;
        this.judge = judge;
        this.interventions = interventions;
    }

    /**
     * 先合成档位再放进上下文。关键词已经是危机时不再问语义判定。
     * 判定失败按升高，避免误换成危机话术。上下文为空时只往下走。
     */
    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext context,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        Assessment assessment = assess(userText(input));
        if (context != null) {
            context.put(RiskLevel.class, assessment.level);
        }
        record(context, assessment);
        return next.apply(input);
    }

    /**
     * 危机档换成 classpath 话术。升高档留下搭子人设，只追加克制标记。
     * 读失败仍用代码兜底，不能把空串交给模型。
     */
    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext context, String currentPrompt) {
        if (critical(context)) {
            return Mono.just(crisisText());
        }
        if (elevated(context)) {
            return Mono.just(markElevated(currentPrompt));
        }
        return Mono.just(currentPrompt == null ? "" : currentPrompt);
    }

    /**
     * 关键词危机直接返回。其它情况再问语义判定。
     * 判定器缺失或返回空按普通陪伴。抛错按升高。
     */
    private Assessment assess(String text) {
        RiskLevel keyword = KeywordRiskClassifier.classify(text);
        if (keyword == RiskLevel.CRITICAL) {
            return new Assessment(RiskLevel.CRITICAL, SafetyInterventionRecorder.SOURCE_KEYWORD);
        }
        if (judge == null) {
            return new Assessment(RiskLevel.NORMAL, SafetyInterventionRecorder.SOURCE_SEMANTIC);
        }
        try {
            RiskLevel semantic = judge.judge(text);
            if (semantic == null || semantic == RiskLevel.NORMAL) {
                return new Assessment(RiskLevel.NORMAL, SafetyInterventionRecorder.SOURCE_SEMANTIC);
            }
            return new Assessment(semantic, SafetyInterventionRecorder.SOURCE_SEMANTIC);
        } catch (RuntimeException ex) {
            return new Assessment(RiskLevel.ELEVATED, SafetyInterventionRecorder.SOURCE_JUDGE_FAILED);
        }
    }

    /**
     * 只记升高和危机。摘要是固定短句，不带用户原文。
     * 写失败只打日志，对话继续。
     */
    private void record(RuntimeContext context, Assessment assessment) {
        if (interventions == null || assessment.level == RiskLevel.NORMAL) {
            return;
        }
        CallIdentity identity = CallIdentity.from(context);
        try {
            interventions.record(
                    identity.tenantId(),
                    identity.userId(),
                    identity.sessionId(),
                    assessment.level.name(),
                    assessment.source,
                    summaryOf(assessment));
        } catch (RuntimeException ex) {
            log.warning("safety intervention record failed");
        }
    }

    /** 危机正文读不到时用代码兜底。 */
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

    /** 已经带过标记就不再追加，避免多轮重复。 */
    private static String markElevated(String current) {
        String base = current == null ? "" : current;
        if (base.contains(ELEVATED_MARK)) {
            return base;
        }
        if (base.isBlank()) {
            return ELEVATED_MARK;
        }
        return base + "\n" + ELEVATED_MARK;
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
        return context != null && context.get(RiskLevel.class) == RiskLevel.CRITICAL;
    }

    /** 升高才加标记。普通陪伴原样留下。 */
    private static boolean elevated(RuntimeContext context) {
        return context != null && context.get(RiskLevel.class) == RiskLevel.ELEVATED;
    }

    /** 摘要只含档位和来源，不含用户原文。 */
    private static String summaryOf(Assessment assessment) {
        if (SafetyInterventionRecorder.SOURCE_KEYWORD.equals(assessment.source)) {
            return "keyword critical";
        }
        if (SafetyInterventionRecorder.SOURCE_JUDGE_FAILED.equals(assessment.source)) {
            return "judge failed";
        }
        if (assessment.level == RiskLevel.CRITICAL) {
            return "semantic critical";
        }
        return "semantic elevated";
    }

    /**
     * 一次判定的结果。来源给落库用，不进模型。
     */
    private record Assessment(RiskLevel level, String source) {
    }
}

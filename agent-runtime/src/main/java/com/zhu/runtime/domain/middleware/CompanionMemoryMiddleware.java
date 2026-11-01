package com.zhu.runtime.domain.middleware;

import com.zhu.runtime.domain.CallIdentity;
import com.zhu.semantic.contract.PersonaContract;
import com.zhu.semantic.contract.RiskLevel;
import com.zhu.state.api.EpisodicMemoryRepository;
import com.zhu.state.api.UserProfileRepository;
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

/**
 * 对话成功结束后写情景记忆和画像。危机不写。
 * 摘要只留用户输入的短句，不存对话全文，也不调用模型。
 * 写失败只打日志，不改变已经生成的回复。
 */
@Component
public class CompanionMemoryMiddleware implements MiddlewareBase {

    private static final Logger log = Logger.getLogger(CompanionMemoryMiddleware.class.getName());

    /** 短摘要上限。表允许 500，这里先压成一句。 */
    static final int SUMMARY_LIMIT = 80;

    private final EpisodicMemoryRepository memories;

    private final UserProfileRepository profiles;

    private final PersonaContract persona;

    public CompanionMemoryMiddleware(
            EpisodicMemoryRepository memories,
            UserProfileRepository profiles,
            PersonaContract persona) {
        this.memories = memories;
        this.profiles = profiles;
        this.persona = persona;
    }

    /**
     * 等这次调用正常结束后再写。失败或取消不落库。
     * 档位由安全门事先放进上下文。
     */
    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext context,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        return next.apply(input).doOnComplete(() -> remember(context, input));
    }

    /**
     * 危机跳过。摘要为空也跳过。两张表分开写，一张失败不影响另一张。
     */
    void remember(RuntimeContext context, AgentInput input) {
        if (critical(context)) {
            return;
        }
        String summary = clip(userText(input));
        if (summary.isEmpty()) {
            return;
        }
        CallIdentity identity = CallIdentity.from(context);
        appendMemory(identity, summary, salience(context));
        upsertProfile(identity);
    }

    /** 普通陪伴 2，升高 4。危机不会走到这里。 */
    static int salience(RuntimeContext context) {
        if (context != null && context.get(RiskLevel.class) == RiskLevel.ELEVATED) {
            return 4;
        }
        return 2;
    }

    /** 换行压成空格，超出上限就截断。不把整段对话留下来。 */
    static String clip(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String compact = text.replace('\r', ' ').replace('\n', ' ').trim();
        if (compact.length() <= SUMMARY_LIMIT) {
            return compact;
        }
        return compact.substring(0, SUMMARY_LIMIT);
    }

    private void appendMemory(CallIdentity identity, String summary, int salience) {
        try {
            memories.append(identity.tenantId(), identity.userId(), identity.sessionId(), summary, salience);
        } catch (RuntimeException ex) {
            log.warning("episodic memory record failed");
        }
    }

    /**
     * 阶段仍用锁定人格里的约定。不读画像表，也不从对话里升级关系。
     */
    private void upsertProfile(CallIdentity identity) {
        try {
            profiles.upsert(identity.tenantId(), identity.userId(), persona.relationshipStage(), null);
        } catch (RuntimeException ex) {
            log.warning("user profile record failed");
        }
    }

    private static boolean critical(RuntimeContext context) {
        return context != null && context.get(RiskLevel.class) == RiskLevel.CRITICAL;
    }

    /** 从后往前找用户消息。助手回复不进摘要。 */
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
}

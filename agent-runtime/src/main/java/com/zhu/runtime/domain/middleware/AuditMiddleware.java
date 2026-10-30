package com.zhu.runtime.domain.middleware;

import com.zhu.audit.api.AuditRecorder;
import com.zhu.runtime.domain.CallIdentity;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 执行审计。包住一次 Agent 调用，只记开始、结束和失败，不记提示词全文。
 * 用 Middleware，不用已标删除的 Hook。
 */
@Component
public class AuditMiddleware implements MiddlewareBase {

    private final AuditRecorder audits;

    public AuditMiddleware(AuditRecorder audits) {
        this.audits = audits;
    }

    /**
     * 调用开始时记一条，正常结束和失败再各记一条。异常信息可能带用户输入，失败只记异常类型。
     */
    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext context,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        CallIdentity identity = CallIdentity.from(context);
        String actor = identity.userId() == null ? "" : identity.userId();
        String detail = "session=" + identity.sessionId();
        audits.record("agent.call", actor, detail);
        try {
            return next.apply(input)
                    .doOnComplete(() -> audits.record("agent.call.finish", actor, detail))
                    .doOnError(error -> audits.record(
                            "agent.call.error", actor, error.getClass().getSimpleName()));
        } catch (RuntimeException error) {
            audits.record("agent.call.error", actor, error.getClass().getSimpleName());
            throw error;
        }
    }
}

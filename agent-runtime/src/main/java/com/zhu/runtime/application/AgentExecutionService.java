package com.zhu.runtime.application;

import com.zhu.common.util.Ids;
import com.zhu.runtime.api.AgentExecutor;
import com.zhu.runtime.api.AgentRequest;
import com.zhu.runtime.api.AgentResponse;
import com.zhu.runtime.api.AgentStreamEvent;
import com.zhu.runtime.infrastructure.agent.HarnessAgentFactory;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 遗留：每请求 build/close 的执行路径。
 * 正式进程默认关闭；仅在 {@code agentscope.runtime.execution=legacy} 时启用。
 * 正式对话走接入层 Channel Gateway。
 */
@Service
@ConditionalOnProperty(name = "agentscope.runtime.execution", havingValue = "legacy")
public class AgentExecutionService implements AgentExecutor {

    private final HarnessAgentFactory factory;

    private final AgentTurnSupport turns;

    /** 注入工厂与共用护栏零件。 */
    public AgentExecutionService(HarnessAgentFactory factory, AgentTurnSupport turns) {
        this.factory = factory;
        this.turns = turns;
    }

    /**
     * 新建 Agent、同步等待回复、然后关闭。不要把返回的 Agent 留到方法外面。
     */
    @Override
    public AgentResponse execute(AgentRequest request) {
        turns.require(request);
        RuntimeContext context = turns.context(request);
        try (HarnessAgent agent = factory.build(request)) {
            long started = System.nanoTime();
            Msg reply = agent.call(request.userText(), context)
                    .block(Duration.ofSeconds(turns.timeoutSeconds()));
            String text = turns.finish(reply == null ? null : reply.getTextContent(), context);
            String runId = AgentTurnSupport.runIdOf(reply);
            turns.recordUsage(request, runId, reply, started);
            return new AgentResponse(runId, text);
        }
    }

    /**
     * 先建好 Agent 和上下文再返回 Flux。订阅结束时关闭 Agent。
     */
    @Override
    public Flux<AgentStreamEvent> stream(AgentRequest request) {
        turns.require(request);
        HarnessAgent agent = factory.build(request);
        RuntimeContext context = turns.context(request);
        long started = System.nanoTime();
        try {
            return agent.streamEvents(request.userText(), context)
                    .doOnNext(event -> {
                        if (event instanceof AgentResultEvent result) {
                            Msg message = result.getResult();
                            String runId = message == null || message.getId() == null ? Ids.newId() : message.getId();
                            turns.recordUsage(request, runId, message, started);
                        }
                    })
                    .concatMapIterable(event -> turns.mapStreamEvent(event, context))
                    .timeout(Duration.ofSeconds(turns.timeoutSeconds()))
                    .doFinally(signal -> close(agent));
        } catch (RuntimeException error) {
            close(agent);
            throw error;
        }
    }

    /** 关闭失败不能盖住已经产生的回复或原始错误。 */
    private static void close(HarnessAgent agent) {
        try {
            agent.close();
        } catch (RuntimeException ignored) {
            // 关闭是收尾，不再向外抛。
        }
    }
}

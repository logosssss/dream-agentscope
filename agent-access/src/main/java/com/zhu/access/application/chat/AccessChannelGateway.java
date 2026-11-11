package com.zhu.access.application.chat;

import com.zhu.common.util.Ids;
import com.zhu.runtime.api.AgentExecutor;
import com.zhu.runtime.api.AgentRequest;
import com.zhu.runtime.api.AgentResponse;
import com.zhu.runtime.api.AgentStreamEvent;
import com.zhu.runtime.application.AgentTurnSupport;
import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.runtime.infrastructure.agent.HarnessAgentFactory;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.GatewayBootstrap;
import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;
import io.agentscope.harness.agent.gateway.channel.chatui.SendOptions;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 接入层 Agent 网关：长驻 companion + echo，经 ChatUiChannel 进出。
 * 同 session 排队由框架 Gateway 负责。实现 {@link AgentExecutor} 供 ChatAppService 注入。
 * 模型密钥缺失不阻断进程启动；首次对话才组装长驻 Agent。
 */
@Service
@Primary
@ConditionalOnProperty(name = "agentscope.runtime.execution", havingValue = "channel", matchIfMissing = true)
public class AccessChannelGateway implements AgentExecutor {

    private static final Logger log = Logger.getLogger(AccessChannelGateway.class.getName());

    private final HarnessAgentFactory agents;

    private final AgentTurnSupport turns;

    private final AgentRuntimeProperties properties;

    private final ObjectProvider<DistributedStore> stores;

    private final Object lock = new Object();

    private GatewayBootstrap bootstrap;

    private ChatUiChannel channel;

    private String mainAgentId;

    /** 只接线，不立刻建模型；缺 DASHSCOPE_API_KEY 时仍允许进程起来。 */
    @Autowired
    public AccessChannelGateway(
            HarnessAgentFactory agents,
            AgentTurnSupport turns,
            AgentRuntimeProperties properties,
            ObjectProvider<DistributedStore> stores) {
        this.agents = agents;
        this.turns = turns;
        this.properties = properties;
        this.stores = stores;
    }

    /**
     * 同步一轮。SendOptions 带上会话与 RuntimeContext，走 Gateway 排队。
     */
    @Override
    public AgentResponse execute(AgentRequest request) {
        turns.require(request);
        RuntimeContext context = turns.context(request);
        long started = System.nanoTime();
        Msg reply = channel()
                .send(options(request, context), request.userText())
                .block(Duration.ofSeconds(turns.timeoutSeconds()));
        String text = turns.finish(reply == null ? null : reply.getTextContent(), context);
        String runId = AgentTurnSupport.runIdOf(reply);
        turns.recordUsage(request, runId, reply, started);
        return new AgentResponse(runId, text);
    }

    /**
     * 流式一轮。事件仍经运行层护栏映射成 SSE 片段。
     */
    @Override
    public Flux<AgentStreamEvent> stream(AgentRequest request) {
        turns.require(request);
        RuntimeContext context = turns.context(request);
        long started = System.nanoTime();
        AtomicReference<String> runId = new AtomicReference<>(Ids.newId());
        return channel()
                .sendStream(options(request, context), request.userText())
                .doOnNext(event -> {
                    if (event instanceof AgentResultEvent result) {
                        Msg message = result.getResult();
                        runId.set(AgentTurnSupport.runIdOf(message));
                        turns.recordUsage(request, runId.get(), message, started);
                    }
                })
                .concatMapIterable(event -> turns.mapStreamEvent(event, context))
                .timeout(Duration.ofSeconds(turns.timeoutSeconds()));
    }

    /** 进程退出时停 Gateway，并关闭长驻 Agent。 */
    @PreDestroy
    public void shutdown() {
        synchronized (lock) {
            if (bootstrap != null) {
                try {
                    bootstrap.stop();
                } catch (RuntimeException error) {
                    log.warning("channel gateway stop failed: " + error.getMessage());
                }
                for (HarnessAgent agent : bootstrap.agents().values()) {
                    closeQuietly(agent);
                }
            }
            bootstrap = null;
            channel = null;
        }
    }

    /** 组装并启动 Gateway。companion 为 main，echo 仅显式 agentId 可达。 */
    private void startGateway() {
        HarnessAgent companion = agents.buildCompanionResident();
        HarnessAgent echo = agents.buildEchoResident();
        mainAgentId = companion.getAgentId() == null || companion.getAgentId().isBlank()
                ? properties.getAgentId()
                : companion.getAgentId();
        if (mainAgentId == null || mainAgentId.isBlank()) {
            mainAgentId = "companion-v1";
        }
        GatewayBootstrap.Builder builder = GatewayBootstrap.builder()
                .agent(mainAgentId, companion)
                .agent(HarnessAgentFactory.ECHO_AGENT_ID, echo)
                .mainAgent(mainAgentId);
        DistributedStore store = stores.getIfAvailable();
        if (store != null) {
            builder.distributedStore(store);
        }
        GatewayBootstrap started = builder.build().start();
        synchronized (lock) {
            this.bootstrap = started;
            this.channel = started.chatUiChannel();
        }
        log.info("channel gateway started main=" + mainAgentId + " echo=" + HarnessAgentFactory.ECHO_AGENT_ID);
    }

    /** 首次对话时再组装 Gateway；已启动则直接返回 Channel。 */
    private ChatUiChannel channel() {
        ChatUiChannel open = channel;
        if (open != null) {
            return open;
        }
        synchronized (lock) {
            if (channel == null) {
                startGateway();
            }
            open = channel;
        }
        if (open == null) {
            throw new IllegalStateException("channel gateway is not started");
        }
        return open;
    }

    /** 会话用请求里的 sessionId；agentId 空则走 main。 */
    private SendOptions options(AgentRequest request, RuntimeContext context) {
        String agentId = turns.agentId(request);
        SendOptions options = SendOptions.of(request.userId(), request.sessionId())
                .withRuntimeContext(context);
        if (agentId != null && !agentId.isBlank() && !agentId.equals(mainAgentId)) {
            options = options.withAgentId(agentId);
        }
        return options;
    }

    private static void closeQuietly(HarnessAgent agent) {
        if (agent == null) {
            return;
        }
        try {
            agent.close();
        } catch (RuntimeException ignored) {
            // 停机收尾。
        }
    }
}

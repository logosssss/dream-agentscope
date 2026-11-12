package com.zhu.access.application.chat.gateway;

import com.zhu.common.util.Ids;
import com.zhu.runtime.api.AgentExecutor;
import com.zhu.runtime.api.AgentRequest;
import com.zhu.runtime.api.AgentResponse;
import com.zhu.runtime.api.AgentStreamEvent;
import com.zhu.runtime.application.AgentTurnSupport;
import com.zhu.runtime.infrastructure.agent.HarnessAgentFactory;
import com.zhu.runtime.infrastructure.agent.HarnessAgentRegistry;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.ChannelManager;
import io.agentscope.harness.agent.gateway.GatewayBootstrap;
import io.agentscope.harness.agent.gateway.LocalSessionTurnGate;
import io.agentscope.harness.agent.gateway.channel.Channel;
import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;
import io.agentscope.harness.agent.gateway.channel.chatui.SendOptions;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 接入层 Agent 网关：长驻 companion + echo，经 ChatUiChannel 进出。
 * Agent 由 {@link GatewayBootstrap} 的 Consumer 创建；工厂只提供公共项与各 Agent 定制。
 * 同 session 串行由框架 {@link LocalSessionTurnGate} 负责（进程内）；启动时显式挂上。
 * 租户等业务字段经 {@link AccessChannelRuntimeContextResolver} 写入 RuntimeContext。
 * ChatUi 在 Bootstrap 上显式注册，之后只从 {@link ChannelManager} 只读取，不挂第二通道。
 * 实现 {@link AgentExecutor}，是对话执行唯一入口。
 * 模型密钥缺失不阻断进程启动；首次对话才组装长驻 Agent。
 * 业务配置热更后经 {@link #rebuildAll()} 排空再重建。
 */
@Service
@Primary
public class AccessChannelGateway implements AgentExecutor, ResidentAgentReloader {

    private static final Logger log = Logger.getLogger(AccessChannelGateway.class.getName());

    private final HarnessAgentFactory agents;

    private final AgentTurnSupport turns;

    private final ObjectProvider<DistributedStore> stores;

    private final Object lock = new Object();

    private final ResidentAgentDrain drain = new ResidentAgentDrain();

    private GatewayBootstrap bootstrap;

    private ChatUiChannel channel;

    private String mainAgentId;

    /** 只接线，不立刻建模型；缺 DASHSCOPE_API_KEY 时仍允许进程起来。 */
    @Autowired
    public AccessChannelGateway(
            HarnessAgentFactory agents,
            AgentTurnSupport turns,
            ObjectProvider<DistributedStore> stores) {
        this.agents = agents;
        this.turns = turns;
        this.stores = stores;
    }

    /**
     * 同步一轮。进 Gateway 的是业务暂存；护栏收口仍用本地完整上下文。
     */
    @Override
    public AgentResponse execute(AgentRequest request) {
        turns.require(request);
        drain.beginTurn();
        try {
            // 本地上下文给 finish/用量；Gateway 侧由 resolver + MsgContext 合并。
            RuntimeContext context = turns.context(request);
            long started = System.nanoTime();
            Msg reply = channel()
                    .send(options(request), request.userText())
                    .block(Duration.ofSeconds(turns.timeoutSeconds()));
            String text = turns.finish(reply == null ? null : reply.getTextContent(), context);
            String runId = AgentTurnSupport.runIdOf(reply);
            turns.recordUsage(request, runId, reply, started);
            return new AgentResponse(runId, text);
        } finally {
            drain.endTurn();
        }
    }

    /**
     * 流式一轮。事件仍经运行层护栏映射成 SSE 片段。
     */
    @Override
    public Flux<AgentStreamEvent> stream(AgentRequest request) {
        turns.require(request);
        drain.beginTurn();
        RuntimeContext context = turns.context(request);
        long started = System.nanoTime();
        AtomicReference<String> runId = new AtomicReference<>(Ids.newId());
        try {
            return channel()
                    .sendStream(options(request), request.userText())
                    .doOnNext(event -> {
                        if (event instanceof AgentResultEvent result) {
                            Msg message = result.getResult();
                            runId.set(AgentTurnSupport.runIdOf(message));
                            turns.recordUsage(request, runId.get(), message, started);
                        }
                    })
                    .concatMapIterable(event -> turns.mapStreamEvent(event, context))
                    .timeout(Duration.ofSeconds(turns.timeoutSeconds()))
                    .doFinally(signal -> drain.endTurn());
        } catch (RuntimeException error) {
            drain.endTurn();
            throw error;
        }
    }

    /**
     * 排空在途对话后停掉旧 Gateway，再按当前配置重建 companion + echo。
     * 尚未懒启动过则空操作。排空上限复用运行超时，避免热更卡死。
     */
    @Override
    public void rebuildAll() {
        Duration timeout = Duration.ofSeconds(Math.max(1, turns.timeoutSeconds()));
        drain.drainAndRun(timeout, () -> {
            synchronized (lock) {
                if (bootstrap == null) {
                    log.info("channel gateway rebuild skipped: not started yet");
                    return;
                }
                stopLocked();
                startGatewayLocked();
                log.info("channel gateway rebuilt main=" + mainAgentId
                        + " echo=" + HarnessAgentFactory.ECHO_AGENT_ID);
            }
        });
    }

    /** 进程退出时停 Gateway，并关闭长驻 Agent。 */
    @PreDestroy
    public void shutdown() {
        drain.drainAndRun(Duration.ofSeconds(Math.max(1, turns.timeoutSeconds())), () -> {
            synchronized (lock) {
                stopLocked();
            }
        });
    }

    /** 组装并启动 Gateway。companion 为 main，echo 仅显式 agentId 可达。调用方须已持有 lock。 */
    private void startGatewayLocked() {
        mainAgentId = agents.companionAgentId();
        // configureAllAgents 必须在 agent(Consumer) 之前：框架先跑公共项再跑各 Agent 定制并立刻 build。
        // 唯一通道在 Builder 上显式挂上；start 后只读 ChannelManager，不再 chatUiChannel() 另造一份。
        GatewayBootstrap.Builder builder = GatewayBootstrap.builder()
                .configureAllAgents(agents::configureCommon)
                .agent(mainAgentId, agents::customizeCompanion)
                .agent(HarnessAgentFactory.ECHO_AGENT_ID, agents::customizeEcho)
                .mainAgent(mainAgentId)
                .channel(ChatUiChannel.create())
                .runtimeContextResolver(new AccessChannelRuntimeContextResolver());
        DistributedStore store = stores.getIfAvailable();
        if (store != null) {
            builder.distributedStore(store);
        }
        GatewayBootstrap started = builder.build().start();
        // 显式挂进程内占轮，避免依赖构造默认值；多副本 / Redis turn 不在本期。
        started.gateway().setSessionTurnGate(new LocalSessionTurnGate());
        HarnessAgent companion = started.agents().get(mainAgentId);
        // Toolkit 在 build 之后才有，业务工具只能这时候挂。
        agents.attachCompanionTools(companion);
        // Middleware 链路里常见 ReAct 委托；危机闸 flush 需按 agentId 回查 Harness。
        HarnessAgentRegistry.clear();
        for (HarnessAgent agent : started.agents().values()) {
            HarnessAgentRegistry.register(agent);
        }
        this.bootstrap = started;
        this.channel = requireChatUi(started.channelManager());
        log.info("channel gateway started main=" + mainAgentId + " echo=" + HarnessAgentFactory.ECHO_AGENT_ID
                + " channels=" + started.channelManager().channelIds());
    }

    /**
     * 从 ChannelManager 只读取出默认 ChatUi，并要求名单里只有这一条。
     * 缺通道或类型不对直接失败，避免静默再 new 一份 ChatUi。
     */
    public static ChatUiChannel requireChatUi(ChannelManager manager) {
        if (manager == null) {
            throw new IllegalStateException("channel manager is missing");
        }
        List<String> ids = manager.channelIds();
        if (ids.size() != 1 || !ChatUiChannel.CHANNEL_ID.equals(ids.get(0))) {
            throw new IllegalStateException("expected only chatui channel, got " + ids);
        }
        Channel bound = manager.getChannel(ChatUiChannel.CHANNEL_ID)
                .orElseThrow(() -> new IllegalStateException("chatui channel missing"));
        if (!(bound instanceof ChatUiChannel chatUi)) {
            throw new IllegalStateException("chatui channel type mismatch: " + bound.getClass().getName());
        }
        return chatUi;
    }

    /** 停掉当前 Gateway 并关闭 Agent。调用方须已持有 lock。 */
    private void stopLocked() {
        if (bootstrap == null) {
            channel = null;
            return;
        }
        try {
            bootstrap.stop();
        } catch (RuntimeException error) {
            log.warning("channel gateway stop failed: " + error.getMessage());
        }
        for (HarnessAgent agent : bootstrap.agents().values()) {
            HarnessAgentRegistry.unregister(agent.getAgentId());
            closeQuietly(agent);
        }
        bootstrap = null;
        channel = null;
    }

    /** 首次对话时再组装 Gateway；已启动则直接返回 Channel。 */
    private ChatUiChannel channel() {
        ChatUiChannel open = channel;
        if (open != null) {
            return open;
        }
        synchronized (lock) {
            if (channel == null) {
                startGatewayLocked();
            }
            open = channel;
        }
        if (open == null) {
            throw new IllegalStateException("channel gateway is not started");
        }
        return open;
    }

    /**
     * 会话用请求里的 sessionId；caller 只带 tenantId/agentId 暂存。
     * agentId 空或等于 main 时不改路由，仍走 Bootstrap 的 mainAgent。
     */
    private SendOptions options(AgentRequest request) {
        String agentId = turns.agentId(request);
        SendOptions options = SendOptions.of(request.userId(), request.sessionId())
                .withRuntimeContext(AccessChannelRuntimeContextResolver.staging(request.tenantId(), agentId));
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

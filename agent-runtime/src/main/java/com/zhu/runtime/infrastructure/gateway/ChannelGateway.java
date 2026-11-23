package com.zhu.runtime.infrastructure.gateway;

import com.zhu.common.util.Ids;
import com.zhu.runtime.api.agent.AgentExecutor;
import com.zhu.runtime.api.agent.AgentRequest;
import com.zhu.runtime.api.agent.AgentResponse;
import com.zhu.runtime.api.agent.AgentStreamEvent;
import com.zhu.runtime.api.agent.RebuildResult;
import com.zhu.runtime.api.agent.ResidentAgentReloader;
import com.zhu.runtime.application.turn.AgentTurnSupport;
import com.zhu.runtime.infrastructure.agent.HarnessAgentFactory;
import com.zhu.runtime.infrastructure.agent.HarnessAgentRegistry;
import com.zhu.runtime.infrastructure.agent.ResidentAgentBinding;
import com.zhu.runtime.infrastructure.agent.ResidentAgentBindingRegistry;
import com.zhu.runtime.infrastructure.approval.PendingAskStore;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.ChannelManager;
import io.agentscope.harness.agent.gateway.GatewayBootstrap;
import io.agentscope.harness.agent.gateway.HarnessGateway;
import io.agentscope.harness.agent.gateway.LocalSessionTurnGate;
import io.agentscope.harness.agent.gateway.channel.Channel;
import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;
import io.agentscope.harness.agent.gateway.channel.chatui.SendOptions;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 运行层 Agent 网关：长驻 companion + echo，经 ChatUiChannel / ApiChannel 进出。
 * Agent 由 {@link GatewayBootstrap} 的 Consumer 创建；工厂只提供公共项与各 Agent 定制。
 * 同 session 串行由框架 {@link LocalSessionTurnGate} 负责（进程内）；启动时显式挂上。
 * 租户等业务字段经 {@link AgentChannelRuntimeContextResolver} 写入 RuntimeContext。
 * ChatUi 与 {@link ApiChannel} 在 Bootstrap 上显式注册，之后只从 {@link ChannelManager} 只读取。
 * 名单固定白名单 {@code chatui}+{@code api}（见 docs/api-channel-落地.md）；WEB 主路径仍只调 ChatUi。
 * 同 session 跨 Channel 串行：{@link RoomNormalizedSessionTurnGate} 包一层进程内闸。
 * 实现 {@link AgentExecutor}，是对话执行唯一入口；续跑由 {@code PendingAskFacade} 经 {@link #resumeConfirm} 调用。
 * 模型密钥缺失不阻断进程启动；首次对话才组装长驻 Agent。
 * 产品 Agent 经 {@link ResidentAgentBindingRegistry} 注册；echo 为平台探针。
 * 业务配置热更后经 {@link #rebuildAll()} 排空再重建。
 * 待确认直写本模块 {@link PendingAskStore}（与 Facade 共用）。
 */
@Service
@Primary
public class ChannelGateway implements AgentExecutor, ResidentAgentReloader {

    private static final Logger log = Logger.getLogger(ChannelGateway.class.getName());

    /**
     * API Channel 的 Harness channelId。与 ChatUi 并列注册时用；本类主路径仍只调用 ChatUi。
     * 见 docs/api-channel-落地.md。
     */
    public static final String API_CHANNEL_ID = "api";

    private final HarnessAgentFactory agents;

    private final ResidentAgentBindingRegistry residentBindings;

    private final AgentTurnSupport turns;

    private final ObjectProvider<DistributedStore> stores;

    private final PendingAskStore pendingAsks;

    private final Object lock = new Object();

    private final ResidentAgentDrain drain = new ResidentAgentDrain();

    private GatewayBootstrap bootstrap;

    /** DCL 无锁快路径发布；须 volatile。 */
    private volatile ChatUiChannel channel;

    /** API Channel；与 ChatUi 一并注册。WEB 主路径不用。DCL 无锁快路径须 volatile。 */
    private volatile ApiChannel apiChannel;

    /** 启动后供 options / 续跑比路由；无锁读取须 volatile。 */
    private volatile String mainAgentId;

    /** 只接线，不立刻建模型；缺 DASHSCOPE_API_KEY 时仍允许进程起来。 */
    @Autowired
    public ChannelGateway(
            HarnessAgentFactory agents,
            ResidentAgentBindingRegistry residentBindings,
            AgentTurnSupport turns,
            ObjectProvider<DistributedStore> stores,
            PendingAskStore pendingAsks) {
        this.agents = agents;
        this.residentBindings = residentBindings;
        this.turns = turns;
        this.stores = stores;
        this.pendingAsks = pendingAsks;
    }

    /**
     * WEB 同步一轮（ChatUi）。进 Gateway 的是业务暂存；护栏收口仍用本地完整上下文。
     * 带图时先发多模态 Msg；失败则降级纯文本重试，不自动换模。
     */
    @Override
    public AgentResponse execute(AgentRequest request) {
        turns.require(request);
        drain.beginTurn();
        try {
            // 本地上下文给 finish/用量；Gateway 侧由 resolver + MsgContext 合并。
            RuntimeContext context = turns.context(request);
            long started = System.nanoTime();
            Msg reply = sendChatUi(request);
            String text = turns.finish(reply == null ? null : reply.getTextContent(), context);
            String runId = AgentTurnSupport.runIdOf(reply);
            turns.recordUsage(request, runId, reply, started);
            return new AgentResponse(runId, text);
        } finally {
            drain.endTurn();
        }
    }

    /**
     * API Channel 同步一轮。与 {@link #execute} 护栏相同，走 {@link ApiChannel}。
     */
    @Override
    public AgentResponse executeApi(AgentRequest request) {
        turns.require(request);
        drain.beginTurn();
        try {
            RuntimeContext context = turns.context(request);
            long started = System.nanoTime();
            String agentId = turns.agentId(request);
            Msg reply = apiChannel()
                    .send(
                            request.userId(),
                            request.sessionId(),
                            blankToNull(agentId),
                            request.userText(),
                            AgentChannelRuntimeContextResolver.staging(request.tenantId(), agentId))
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
     * WEB 流式一轮（ChatUi）。事件仍经运行层护栏映射成 SSE 片段。
     * 遇到 RequireUserConfirm 先写入 {@link PendingAskStore}，再映射给前端。
     * 带图失败时降级纯文本并先推一条状态提示。
     */
    @Override
    public Flux<AgentStreamEvent> stream(AgentRequest request) {
        turns.require(request);
        drain.beginTurn();
        // context / 懒启动若同步抛错须 endTurn；成功路径靠 doFinally。
        try {
            RuntimeContext context = turns.context(request);
            long started = System.nanoTime();
            AtomicReference<String> runId = new AtomicReference<>(Ids.newId());
            SendOptions opts = options(request);
            Msg userMsg = AgentUserMessages.of(request);
            Flux<AgentStreamEvent> primary = channel()
                    .sendStream(opts, userMsg)
                    .doOnNext(event -> onStreamEvent(request, runId, started, event))
                    .concatMapIterable(event -> turns.mapStreamEvent(event, context));
            if (!request.hasImages()) {
                return primary
                        .timeout(Duration.ofSeconds(turns.timeoutSeconds()))
                        .doFinally(signal -> drain.endTurn());
            }
            return primary
                    .onErrorResume(error -> {
                        log.warning(() -> "vision stream failed, degrade to text: " + error.getClass().getSimpleName());
                        Msg plain = AgentUserMessages.textOnly(AgentUserMessages.degradedText(request));
                        Flux<AgentStreamEvent> tip = Flux.just(
                                new AgentStreamEvent("tool_call", "未能看图，已按纯文本继续"));
                        Flux<AgentStreamEvent> retry = channel()
                                .sendStream(opts, plain)
                                .doOnNext(event -> onStreamEvent(request, runId, started, event))
                                .concatMapIterable(event -> turns.mapStreamEvent(event, context));
                        return tip.concatWith(retry);
                    })
                    .timeout(Duration.ofSeconds(turns.timeoutSeconds()))
                    .doFinally(signal -> drain.endTurn());
        } catch (RuntimeException error) {
            drain.endTurn();
            throw error;
        }
    }

    /**
     * ChatUi 同步发送；有图失败则降级纯文本。
     */
    private Msg sendChatUi(AgentRequest request) {
        SendOptions opts = options(request);
        Msg userMsg = AgentUserMessages.of(request);
        try {
            return channel().send(opts, userMsg).block(Duration.ofSeconds(turns.timeoutSeconds()));
        } catch (RuntimeException error) {
            if (!request.hasImages()) {
                throw error;
            }
            log.warning(() -> "vision send failed, degrade to text: " + error.getClass().getSimpleName());
            return channel()
                    .send(opts, AgentUserMessages.degradedText(request))
                    .block(Duration.ofSeconds(turns.timeoutSeconds()));
        }
    }

    /**
     * API Channel 流式一轮。
     */
    @Override
    public Flux<AgentStreamEvent> streamApi(AgentRequest request) {
        turns.require(request);
        drain.beginTurn();
        // 与 stream 相同：同步准备失败须放行排水闸。
        try {
            RuntimeContext context = turns.context(request);
            long started = System.nanoTime();
            AtomicReference<String> runId = new AtomicReference<>(Ids.newId());
            String agentId = turns.agentId(request);
            return apiChannel()
                    .sendStream(
                            request.userId(),
                            request.sessionId(),
                            blankToNull(agentId),
                            request.userText(),
                            AgentChannelRuntimeContextResolver.staging(request.tenantId(), agentId))
                    .doOnNext(event -> onStreamEvent(request, runId, started, event))
                    .concatMapIterable(event -> turns.mapStreamEvent(event, context))
                    .timeout(Duration.ofSeconds(turns.timeoutSeconds()))
                    .doFinally(signal -> drain.endTurn());
        } catch (RuntimeException error) {
            drain.endTurn();
            throw error;
        }
    }

    /**
     * 流式事件：待确认入仓；结果记用量。
     */
    private void onStreamEvent(
            AgentRequest request,
            AtomicReference<String> runId,
            long started,
            io.agentscope.core.event.AgentEvent event) {
        if (event instanceof RequireUserConfirmEvent confirm) {
            pendingAsks.remember(
                    request.tenantId(),
                    request.userId(),
                    request.sessionId(),
                    turns.agentId(request),
                    confirm.getToolCalls());
        }
        if (event instanceof AgentResultEvent result) {
            Msg message = result.getResult();
            runId.set(AgentTurnSupport.runIdOf(message));
            turns.recordUsage(request, runId.get(), message, started);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Gate ASK 续跑：用户 Msg 带 {@link Msg#METADATA_CONFIRM_RESULTS}。
     * 由 {@code PendingAskFacade} 在原子 take 后调用；不写审批表。
     */
    public void resumeConfirm(
            String tenantId,
            String userId,
            String sessionId,
            String agentId,
            ToolUseBlock toolCall,
            boolean approved,
            String reason) {
        if (toolCall == null) {
            throw new IllegalArgumentException("tool call is required");
        }
        drain.beginTurn();
        try {
            ConfirmResult confirmed = new ConfirmResult(approved, toolCall);
            String text = reason == null || reason.isBlank()
                    ? (approved ? "approved" : "rejected")
                    : reason.trim();
            Msg confirm = Msg.builder()
                    .role(MsgRole.USER)
                    .textContent(text)
                    .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, List.of(confirmed)))
                    .build();
            // 先确保 Gateway 已起，再比 mainAgentId，避免续跑时尚未懒启动。
            ChatUiChannel open = channel();
            SendOptions options = SendOptions.of(userId, sessionId)
                    .withRuntimeContext(AgentChannelRuntimeContextResolver.staging(tenantId, agentId));
            if (agentId != null && !agentId.isBlank()
                    && (mainAgentId == null || !agentId.equals(mainAgentId))) {
                options = options.withAgentId(agentId);
            }
            open.send(options, confirm).block(Duration.ofSeconds(turns.timeoutSeconds()));
        } finally {
            drain.endTurn();
        }
    }

    /**
     * 排空在途对话后停掉旧 Gateway，再按当前配置重建 companion + echo。
     * 尚未懒启动过则 SKIPPED。排空上限复用运行超时；超时仍重建并标 TIMEOUT。
     */
    @Override
    public RebuildResult rebuildAll() {
        Duration timeout = Duration.ofSeconds(Math.max(1, turns.timeoutSeconds()));
        AtomicBoolean skipped = new AtomicBoolean(false);
        AtomicReference<String> failMessage = new AtomicReference<>();
        boolean drainTimedOut;
        try {
            drainTimedOut = drain.drainAndRun(timeout, () -> {
                synchronized (lock) {
                    if (bootstrap == null) {
                        skipped.set(true);
                        log.info("channel gateway rebuild skipped: not started yet");
                        return;
                    }
                    try {
                        stopLocked();
                        startGatewayLocked();
                        log.info("channel gateway rebuilt main=" + mainAgentId
                                + " echo=" + HarnessAgentFactory.ECHO_AGENT_ID);
                    } catch (RuntimeException error) {
                        failMessage.set(error.getMessage());
                        log.warning("channel gateway rebuild failed: " + error.getMessage());
                    }
                }
            });
        } catch (RuntimeException error) {
            return RebuildResult.failed(error.getMessage());
        }
        if (failMessage.get() != null) {
            return RebuildResult.failed(failMessage.get());
        }
        if (skipped.get()) {
            return RebuildResult.skipped("not started yet");
        }
        if (drainTimedOut) {
            return RebuildResult.timeout("drain timed out; rebuilt with in-flight risk");
        }
        return RebuildResult.success();
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

    /**
     * 供 Wakeup Target 懒解析当前 HarnessGateway；尚未懒启动时返回 null。
     * 不对外暴露 Bootstrap 其它部件。
     */
    public HarnessGateway harnessGatewayOrNull() {
        synchronized (lock) {
            return bootstrap == null ? null : bootstrap.gateway();
        }
    }

    /**
     * 组装并启动 Gateway。产品 Agent 来自 {@link ResidentAgentBindingRegistry}；
     * echo 为平台探针，仅显式 agentId 可达。调用方须已持有 lock。
     */
    private void startGatewayLocked() {
        ResidentAgentBinding main = residentBindings.main();
        mainAgentId = main.agentId();
        // configureAllAgents 必须在 agent(Consumer) 之前：框架先跑公共项再跑各 Agent 定制并立刻 build。
        GatewayBootstrap.Builder builder = GatewayBootstrap.builder()
                .configureAllAgents(agents::configureCommon);
        for (ResidentAgentBinding binding : residentBindings.all()) {
            builder.agent(binding.agentId(), binding::customize);
        }
        builder.agent(HarnessAgentFactory.ECHO_AGENT_ID, agents::customizeEcho)
                .mainAgent(mainAgentId)
                .channel(ChatUiChannel.create(), ApiChannel.create())
                .runtimeContextResolver(new AgentChannelRuntimeContextResolver());
        DistributedStore store = stores.getIfAvailable();
        if (store != null) {
            builder.distributedStore(store);
        }
        GatewayBootstrap started = builder.build().start();
        try {
            // room 归一化：跨 Channel 同 session 串行；多副本 / Redis turn 不在本期。
            started.gateway().setSessionTurnGate(new RoomNormalizedSessionTurnGate(new LocalSessionTurnGate()));
            // Toolkit 在 build 之后才有；各产品 Binding 自行挂工具。
            for (ResidentAgentBinding binding : residentBindings.all()) {
                HarnessAgent agent = started.agents().get(binding.agentId());
                binding.attachTools(agent);
            }
            // Middleware 链路里常见 ReAct 委托；危机闸 flush 需按 agentId 回查 Harness。
            HarnessAgentRegistry.clear();
            for (HarnessAgent agent : started.agents().values()) {
                HarnessAgentRegistry.register(agent);
            }
            this.bootstrap = started;
            this.channel = requireChatUi(started.channelManager());
            this.apiChannel = requireApiChannel(started.channelManager());
        } catch (RuntimeException error) {
            // 字段尚未发布；只停局部 started，避免泄漏已 start 的 Bootstrap。
            try {
                started.stop();
            } catch (RuntimeException stopError) {
                log.warning("channel gateway start rollback stop failed: " + stopError.getMessage());
            }
            throw error;
        }
        log.info("channel gateway started main=" + mainAgentId + " echo=" + HarnessAgentFactory.ECHO_AGENT_ID
                + " products=" + residentBindings.all().size()
                + " channels=" + started.channelManager().channelIds());
    }

    /**
     * 从 ChannelManager 只读取出 ChatUi。
     * 允许名单：仅 {@code chatui}，或恰好 {@code chatui}+{@link #API_CHANNEL_ID}（顺序不限）。
     * 缺 chatui、多出其它 id、或类型不对直接失败，避免静默再 new 一份 ChatUi。
     */
    public static ChatUiChannel requireChatUi(ChannelManager manager) {
        if (manager == null) {
            throw new IllegalStateException("channel manager is missing");
        }
        List<String> ids = manager.channelIds();
        if (!allowedChannelIds(ids)) {
            throw new IllegalStateException("expected chatui or chatui+api channels, got " + ids);
        }
        Channel bound = manager.getChannel(ChatUiChannel.CHANNEL_ID)
                .orElseThrow(() -> new IllegalStateException("chatui channel missing"));
        if (!(bound instanceof ChatUiChannel chatUi)) {
            throw new IllegalStateException("chatui channel type mismatch: " + bound.getClass().getName());
        }
        return chatUi;
    }

    /**
     * 固定白名单：只有 chatui，或 chatui 与 api 各一条。
     * 正式启动走双通道；仅 chatui 留给历史/单测对照。
     */
    public static boolean allowedChannelIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        if (ids.size() == 1) {
            return ChatUiChannel.CHANNEL_ID.equals(ids.get(0));
        }
        if (ids.size() != 2) {
            return false;
        }
        return ids.contains(ChatUiChannel.CHANNEL_ID) && ids.contains(API_CHANNEL_ID);
    }

    /**
     * 从 ChannelManager 只读取 API Channel；须已在白名单内且类型正确。
     */
    public static ApiChannel requireApiChannel(ChannelManager manager) {
        if (manager == null) {
            throw new IllegalStateException("channel manager is missing");
        }
        if (!allowedChannelIds(manager.channelIds()) || !manager.channelIds().contains(API_CHANNEL_ID)) {
            throw new IllegalStateException("expected api channel among " + manager.channelIds());
        }
        Channel bound = manager.getChannel(API_CHANNEL_ID)
                .orElseThrow(() -> new IllegalStateException("api channel missing"));
        if (!(bound instanceof ApiChannel api)) {
            throw new IllegalStateException("api channel type mismatch: " + bound.getClass().getName());
        }
        return api;
    }

    /**
     * 供 API HTTP 取已启动的 ApiChannel；尚未懒启动时启动 Gateway。
     */
    public ApiChannel apiChannel() {
        ApiChannel open = apiChannel;
        if (open != null) {
            return open;
        }
        synchronized (lock) {
            if (apiChannel == null) {
                startGatewayLocked();
            }
            open = apiChannel;
        }
        if (open == null) {
            throw new IllegalStateException("api channel gateway is not started");
        }
        return open;
    }

    /** 停掉当前 Gateway 并关闭 Agent。调用方须已持有 lock。待确认随 Agent 失效一并清掉。 */
    private void stopLocked() {
        if (bootstrap == null) {
            channel = null;
            apiChannel = null;
            clearPendingConfirms();
            return;
        }
        // 先快照 agents：stop() 失败或清空 map 后仍能 unregister / close。
        List<HarnessAgent> agentsSnapshot = new ArrayList<>(bootstrap.agents().values());
        try {
            bootstrap.stop();
        } catch (RuntimeException error) {
            log.warning("channel gateway stop failed: " + error.getMessage());
        }
        for (HarnessAgent agent : agentsSnapshot) {
            HarnessAgentRegistry.unregister(agent.getAgentId());
            closeQuietly(agent);
        }
        bootstrap = null;
        channel = null;
        apiChannel = null;
        clearPendingConfirms();
    }

    private void clearPendingConfirms() {
        pendingAsks.clear();
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
                .withRuntimeContext(AgentChannelRuntimeContextResolver.staging(request.tenantId(), agentId));
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

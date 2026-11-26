package com.zhu.runtime.infrastructure.gateway;

import com.zhu.product.api.ProductModule;
import com.zhu.runtime.api.agent.AgentExecutor;
import com.zhu.runtime.api.agent.AgentRequest;
import com.zhu.runtime.api.agent.AgentResponse;
import com.zhu.runtime.api.agent.AgentStreamEvent;
import com.zhu.runtime.api.agent.RebuildResult;
import com.zhu.runtime.api.agent.ResidentAgentReloader;
import com.zhu.runtime.application.turn.AgentTurnSupport;
import com.zhu.runtime.domain.TurnSignals;
import com.zhu.runtime.infrastructure.agent.HarnessAgentFactory;
import com.zhu.runtime.infrastructure.agent.HarnessAgentRegistry;
import com.zhu.runtime.infrastructure.agent.ProductModuleRegistry;
import com.zhu.runtime.infrastructure.approval.PendingAskStore;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
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
 * 产品 Agent 经 {@link ProductModuleRegistry} 注册；echo 为平台探针。
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

    private final ProductModuleRegistry products;

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
            ProductModuleRegistry products,
            AgentTurnSupport turns,
            ObjectProvider<DistributedStore> stores,
            PendingAskStore pendingAsks) {
        this.agents = agents;
        this.products = products;
        this.turns = turns;
        this.stores = stores;
        this.pendingAsks = pendingAsks;
    }

    /**
     * WEB 同步一轮（ChatUi）。发送参数要带上这一轮的运行号，审计中间件才挂得住行。
     * 带图时先发多模态 Msg；失败则降级纯文本重试，不自动换模。
     * 请求里的 agentId 为空或等于 main 时不改路由；WEB 入口在接入层就把 agentId 清空。
     */
    @Override
    public AgentResponse execute(AgentRequest request) {
        return SyncAgentTurn.run(turns, drain, request, (runId, signals) -> sendChatUi(request, runId, signals));
    }

    /**
     * API Channel 同步一轮。与 {@link #execute} 护栏相同，走 {@link ApiChannel}。
     */
    @Override
    public AgentResponse executeApi(AgentRequest request) {
        return SyncAgentTurn.run(turns, drain, request, (runId, signals) -> {
            return apiChannel()
                    .send(apiTarget(request, runId, signals))
                    .block(Duration.ofSeconds(turns.timeoutSeconds()));
        });
    }

    /**
     * WEB 流式一轮（ChatUi）。事件仍经运行层护栏映射成 SSE 片段。
     * 遇到 RequireUserConfirm 先写入 {@link PendingAskStore}，再映射给前端。
     * 带图失败时降级纯文本并先推一条状态提示。
     */
    @Override
    public Flux<AgentStreamEvent> stream(AgentRequest request) {
        return StreamAgentTurn.run(turns, drain, request, Duration.ofSeconds(turns.timeoutSeconds()),
                (runId, signals, context, started) -> {
                    ChatUiAttempt attempt = ChatUiAttempt.open(options(request, runId, signals), request);
                    return attempt.stream(
                            (opts, msg) -> watch(
                                    channel().sendStream(opts, msg), request, runId, started, context),
                            error -> log.warning(() -> "vision stream failed, degrade to text: "
                                    + error.getClass().getSimpleName()));
                });
    }

    /**
     * ChatUi 同步发送；有图失败则降级纯文本。运行号与共享信号由 {@link SyncAgentTurn} 定好传进来。
     */
    private Msg sendChatUi(AgentRequest request, String runId, TurnSignals signals) {
        ChatUiAttempt attempt = ChatUiAttempt.open(options(request, runId, signals), request);
        return attempt.send(
                (opts, msg) -> channel().send(opts, msg).block(Duration.ofSeconds(turns.timeoutSeconds())),
                error -> log.warning(() -> "vision send failed, degrade to text: "
                        + error.getClass().getSimpleName()));
    }

    /**
     * API Channel 流式一轮。
     */
    @Override
    public Flux<AgentStreamEvent> streamApi(AgentRequest request) {
        return StreamAgentTurn.run(turns, drain, request, Duration.ofSeconds(turns.timeoutSeconds()),
                (runId, signals, context, started) -> {
                    return watch(
                            apiChannel().sendStream(apiTarget(request, runId, signals)),
                            request,
                            runId,
                            started,
                            context);
                });
    }

    /**
     * 先记账再映射。待确认和用量看框架原事件，调用方看护栏之后的片段。
     */
    private Flux<AgentStreamEvent> watch(
            Flux<? extends AgentEvent> raw,
            AgentRequest request,
            String runId,
            long started,
            RuntimeContext context) {
        return WatchedStream.of(
                raw,
                event -> onStreamEvent(request, runId, started, event),
                event -> turns.mapStreamEvent(event, context));
    }

    /**
     * 流式事件：待确认入仓；结果记用量。运行标识在入口就定了，这里不再改写。
     */
    private void onStreamEvent(
            AgentRequest request,
            String runId,
            long started,
            AgentEvent event) {
        if (event instanceof RequireUserConfirmEvent confirm) {
            pendingAsks.remember(
                    request.tenantId(),
                    request.userId(),
                    request.sessionId(),
                    turns.agentId(request),
                    confirm.getToolCalls());
        }
        if (event instanceof AgentResultEvent result) {
            turns.recordUsage(request, runId, result.getResult(), started);
        }
    }

    /**
     * 标识先按运行配置补上默认值，再要求这个产品已经注册。运行号与共享信号进 caller 暂存。
     */
    private ApiOutbound.Target apiTarget(AgentRequest request, String runId, TurnSignals signals) {
        return ApiOutbound.open(
                request, turns.agentId(request), id -> products.find(id) != null, runId, signals);
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
            // 先懒启动再比 main，避免续跑时 mainAgentId 还是 null。
            ChatUiChannel open = channel();
            // 续跑是 Gate 的下半段，不写 agent_run；没有运行行就不给审计编一个对不上的号。
            // 也没有新一轮的安全/人设信号：这一轮的门禁判断在第一次执行时已经做过。
            SendOptions options =
                    ChatUiRoute.options(userId, sessionId, tenantId, agentId, mainAgentId, null, null);
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
     * 组装并启动 Gateway。产品 Agent 来自 {@link ProductModuleRegistry}；
     * echo 为平台探针，仅显式 agentId 可达。调用方须已持有 lock。
     */
    private void startGatewayLocked() {
        ProductModule main = products.main();
        mainAgentId = main.agentId();
        // configureAllAgents 必须在 agent(Consumer) 之前：框架先跑公共项再跑各 Agent 定制并立刻 build。
        GatewayBootstrap.Builder builder = GatewayBootstrap.builder()
                .configureAllAgents(agents::configureCommon);
        for (ProductModule product : products.all()) {
            builder.agent(product.agentId(), product::customize);
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
            // Toolkit 在 build 之后才有；各产品模块自行挂工具。
            for (ProductModule product : products.all()) {
                HarnessAgent agent = started.agents().get(product.agentId());
                product.attachTools(agent);
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
                + " products=" + products.all().size()
                + " channels=" + started.channelManager().channelIds());
    }

    /**
     * 从 ChannelManager 只读取出 ChatUi。
     * 允许名单：仅 {@code chatui}，或恰好 {@code chatui}+{@link #API_CHANNEL_ID}（顺序不限）。
     * 缺 chatui、多出其它 id、或类型不对直接失败，避免静默再 new 一份 ChatUi。
     */
    public static ChatUiChannel requireChatUi(ChannelManager manager) {
        ChannelManager present = BoundChannel.present(manager);
        List<String> ids = present.channelIds();
        if (!allowedChannelIds(ids)) {
            throw new IllegalStateException("expected chatui or chatui+api channels, got " + ids);
        }
        return BoundChannel.read(
                present.getChannel(ChatUiChannel.CHANNEL_ID).orElse(null), ChatUiChannel.class, "chatui");
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
        ChannelManager present = BoundChannel.present(manager);
        List<String> ids = present.channelIds();
        if (!allowedChannelIds(ids) || !ids.contains(API_CHANNEL_ID)) {
            throw new IllegalStateException("expected api channel among " + ids);
        }
        return BoundChannel.read(present.getChannel(API_CHANNEL_ID).orElse(null), ApiChannel.class, "api");
    }

    /**
     * 供 API HTTP 取已启动的 ApiChannel；尚未懒启动时启动 Gateway。
     */
    public ApiChannel apiChannel() {
        return StartedChannel.open(
                lock, () -> apiChannel, this::startGatewayLocked, "api channel gateway is not started");
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
        return StartedChannel.open(lock, () -> channel, this::startGatewayLocked, "channel gateway is not started");
    }

    /**
     * 会话用请求里的 sessionId；caller 带 tenantId/agentId/runId 暂存与这一轮的共享信号。
     * 先懒启动，再比 main。agentId 空或等于 main 时不改路由。
     */
    private SendOptions options(AgentRequest request, String runId, TurnSignals signals) {
        channel();
        return ChatUiRoute.options(
                request.userId(),
                request.sessionId(),
                request.tenantId(),
                turns.agentId(request),
                mainAgentId,
                runId,
                signals);
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

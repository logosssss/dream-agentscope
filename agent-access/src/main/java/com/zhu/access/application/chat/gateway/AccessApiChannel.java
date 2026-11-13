package com.zhu.access.application.chat.gateway;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.harness.agent.gateway.Gateway;
import io.agentscope.harness.agent.gateway.MsgContext;
import io.agentscope.harness.agent.gateway.channel.Channel;
import io.agentscope.harness.agent.gateway.channel.ChannelConfig;
import io.agentscope.harness.agent.gateway.channel.InboundMessage;
import io.agentscope.harness.agent.gateway.channel.OutboundAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 接入层 API Channel（Harness {@code channelId=api}）。
 * 与 ChatUi 并列注册；程序调用对话走本通道的 {@link #send}/{@link #sendStream}。
 * WEB 主路径仍用 ChatUi，不经过这里。见 docs/api-channel-落地.md。
 */
public final class AccessApiChannel implements Channel {

    private final ChannelConfig config = ChannelConfig.of(AccessChannelGateway.API_CHANNEL_ID);

    private Gateway gateway;

    private AccessApiChannel() {
    }

    /**
     * 新建未绑定 Gateway 的实例；由 Bootstrap {@code .channel(...)} 注册后 {@link #init}。
     */
    public static AccessApiChannel create() {
        return new AccessApiChannel();
    }

    /**
     * 框架启动时注入 Gateway；之后 send 才能跑。
     */
    @Override
    public void init(Gateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    @Override
    public String channelId() {
        return AccessChannelGateway.API_CHANNEL_ID;
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    /**
     * 同步一轮：身份与会话由调用方传入（接入层已从登录会话解析）。
     */
    public Mono<Msg> send(String userId, String sessionId, String text) {
        return send(userId, sessionId, null, text, null);
    }

    /**
     * 同步一轮；可选 agentId 与 RuntimeContext（租户暂存等）。
     */
    public Mono<Msg> send(
            String userId, String sessionId, String agentId, String text, RuntimeContext runtimeContext) {
        List<Msg> messages = List.of(userMessage(text));
        return resolveGateway()
                .run(buildContext(userId, sessionId, agentId), messages, outbound(sessionId), runtimeContext);
    }

    /**
     * 流式一轮。
     */
    public Flux<AgentEvent> sendStream(String userId, String sessionId, String text) {
        return sendStream(userId, sessionId, null, text, null);
    }

    /**
     * 流式一轮；可选 agentId 与 RuntimeContext。
     */
    public Flux<AgentEvent> sendStream(
            String userId, String sessionId, String agentId, String text, RuntimeContext runtimeContext) {
        List<Msg> messages = List.of(userMessage(text));
        return resolveGateway()
                .runStream(buildContext(userId, sessionId, agentId), messages, outbound(sessionId), runtimeContext);
    }

    /**
     * Channel 接口入口：把 InboundMessage 转成与 {@link #send} 相同的 MsgContext 形状再跑。
     * 产品 HTTP 优先走 {@link #send}；本方法保证框架侧 dispatch 也占同一套闸键规则。
     */
    @Override
    public Mono<Msg> dispatch(InboundMessage message) {
        Objects.requireNonNull(message, "message");
        String sessionId = message.peer() == null ? "" : message.peer().id();
        String userId = message.senderId() == null ? sessionId : message.senderId();
        return resolveGateway()
                .run(
                        buildContext(userId, sessionId, message.preferredAgentId()),
                        message.messages(),
                        outbound(sessionId),
                        message.runtimeContext(),
                        message);
    }

    /**
     * 流式 dispatch，语义同 {@link #dispatch}。
     */
    @Override
    public Flux<AgentEvent> dispatchStream(InboundMessage message) {
        Objects.requireNonNull(message, "message");
        String sessionId = message.peer() == null ? "" : message.peer().id();
        String userId = message.senderId() == null ? sessionId : message.senderId();
        return resolveGateway()
                .runStream(
                        buildContext(userId, sessionId, message.preferredAgentId()),
                        message.messages(),
                        outbound(sessionId),
                        message.runtimeContext(),
                        message);
    }

    /**
     * 与 ChatUi 对齐：channel=api、room=session，便于 room 归一化闸跨 Channel 串行。
     */
    private MsgContext buildContext(String userId, String sessionId, String agentId) {
        Map<String, String> extra = (agentId == null || agentId.isBlank())
                ? Map.of()
                : Map.of("agentId", agentId);
        return new MsgContext(AccessChannelGateway.API_CHANNEL_ID, null, sessionId, null, null, extra)
                .withUserId(userId);
    }

    private OutboundAddress outbound(String sessionId) {
        return OutboundAddress.direct(AccessChannelGateway.API_CHANNEL_ID, sessionId);
    }

    private static Msg userMessage(String text) {
        return Msg.builder().role(MsgRole.USER).textContent(text == null ? "" : text).build();
    }

    private Gateway resolveGateway() {
        Gateway open = gateway;
        if (open == null) {
            throw new IllegalStateException("api channel is not initialized");
        }
        return open;
    }
}

package com.zhu.access.application.chat.gateway;

import com.zhu.common.security.WakeupTokens;
import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.runtime.infrastructure.wakeup.InMemoryMessageBus;
import io.agentscope.harness.agent.bus.MessageBus;
import io.agentscope.harness.agent.gateway.HarnessGateway;
import io.agentscope.harness.agent.gateway.WakeupDispatcher;
import jakarta.annotation.PreDestroy;
import java.util.Objects;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 接入层：Wakeup 进程内 Bus + Dispatcher 生命周期。
 * enabled=true 时启动期装配；Target 懒解析当前 {@link HarnessGateway}（仅 companion / main）。
 * 忙会话跳过由框架 Dispatcher 完成。见 docs/wakeup-落地.md。
 */
@Component
@Order(20)
public class WakeupCoordinator implements ApplicationRunner {

    private static final Logger log = Logger.getLogger(WakeupCoordinator.class.getName());

    private final AgentRuntimeProperties properties;

    private final ObjectProvider<AccessChannelGateway> gateways;

    private final String wakeupToken;

    private final Object lock = new Object();

    private MessageBus bus;

    private WakeupDispatcher dispatcher;

    /**
     * 令牌来自 {@code agentscope.wakeup.token}（环境变量覆盖）；网关用 ObjectProvider 打破循环依赖。
     */
    public WakeupCoordinator(
            AgentRuntimeProperties properties,
            ObjectProvider<AccessChannelGateway> gateways,
            @Value("${agentscope.wakeup.token:}") String wakeupToken) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.gateways = Objects.requireNonNull(gateways, "gateways");
        this.wakeupToken = wakeupToken == null ? "" : wakeupToken;
    }

    /** 配置是否打开唤醒面。 */
    public boolean isEnabled() {
        return properties.getWakeup() != null && properties.getWakeup().isEnabled();
    }

    /**
     * enabled 时校验令牌并启动 Dispatcher；失败抛错让进程退出（硬失败）。
     * disabled 时空操作。
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!isEnabled()) {
            log.info("wakeup disabled; dispatcher not started");
            return;
        }
        WakeupTokens.requireSecure("agentscope.wakeup.token", wakeupToken);
        startDispatcherLocked();
        log.info("wakeup dispatcher started (in-memory bus)");
    }

    /**
     * 学习入口 enqueue。未启用返回 false；启用则写入 Bus。
     */
    public Mono<Boolean> enqueue(String userId, String sessionId, String agentId) {
        if (!isEnabled()) {
            return Mono.just(false);
        }
        MessageBus active = requireBus();
        String user = userId == null ? "" : userId;
        String session = sessionId == null ? "" : sessionId;
        String agent = agentId == null ? "" : agentId;
        log.info(() -> "wakeup enqueue userId=" + user + ", sessionId=" + session + ", agentId=" + agent);
        return active.enqueueWakeup(user, session, agent).thenReturn(true);
    }

    /** 进程退出时停 Dispatcher 并关 Bus。 */
    @PreDestroy
    public void shutdown() {
        synchronized (lock) {
            stopDispatcherLocked();
            if (bus != null) {
                try {
                    bus.close();
                } catch (RuntimeException ex) {
                    log.warning("wakeup bus close failed: " + ex.getMessage());
                }
                bus = null;
            }
        }
    }

    private void startDispatcherLocked() {
        synchronized (lock) {
            if (dispatcher != null) {
                return;
            }
            MessageBus active = requireBusUnlocked();
            try {
                WakeupDispatcher started = new WakeupDispatcher(active, new LazyGatewayTarget());
                started.start();
                this.dispatcher = started;
            } catch (RuntimeException ex) {
                throw new IllegalStateException(
                        "wakeup.enabled=true but WakeupDispatcher failed to start: " + ex.getMessage(), ex);
            }
        }
    }

    private MessageBus requireBus() {
        synchronized (lock) {
            return requireBusUnlocked();
        }
    }

    private MessageBus requireBusUnlocked() {
        if (bus == null) {
            bus = new InMemoryMessageBus();
        }
        return bus;
    }

    private void stopDispatcherLocked() {
        if (dispatcher == null) {
            return;
        }
        try {
            dispatcher.close();
        } catch (RuntimeException ex) {
            log.warning("wakeup dispatcher stop failed: " + ex.getMessage());
        }
        dispatcher = null;
    }

    /**
     * 懒解析当前 Gateway：未懒启动时 isSessionRunning=false、runWakeup 空完成。
     * HarnessGateway 已实现 WakeupTarget，且 runWakeup 走 main（companion）。
     */
    private final class LazyGatewayTarget implements WakeupDispatcher.WakeupTarget {

        @Override
        public boolean isSessionRunning(String sessionId) {
            HarnessGateway gateway = currentGateway();
            return gateway != null && gateway.isSessionRunning(sessionId);
        }

        @Override
        public Mono<io.agentscope.core.message.Msg> runWakeup(String sessionId) {
            return runWakeup(null, sessionId);
        }

        @Override
        public Mono<io.agentscope.core.message.Msg> runWakeup(String userId, String sessionId) {
            HarnessGateway gateway = currentGateway();
            if (gateway == null) {
                log.warning("wakeup run skipped: channel gateway not started yet, sessionId=" + sessionId);
                return Mono.empty();
            }
            return gateway.runWakeup(userId, sessionId);
        }

        private HarnessGateway currentGateway() {
            AccessChannelGateway access = gateways.getIfAvailable();
            return access == null ? null : access.harnessGatewayOrNull();
        }
    }
}

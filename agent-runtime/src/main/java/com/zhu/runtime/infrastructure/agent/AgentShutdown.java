package com.zhu.runtime.infrastructure.agent;

import com.zhu.runtime.config.AgentRuntimeProperties;
import io.agentscope.core.shutdown.GracefulShutdownConfig;
import io.agentscope.core.shutdown.GracefulShutdownManager;
import io.agentscope.core.shutdown.GracefulShutdownMiddleware;
import io.agentscope.core.shutdown.PartialReasoningPolicy;
import java.time.Duration;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 进程停机时不再接新的 Agent 调用，并等待已经开始的调用结束。
 * 框架在第一次拿到管理器时注册 JVM hook。这里放到启动完成后再拿，避免停机顺序抢在 Web 容器前面。
 */
@Component
public class AgentShutdown implements ApplicationRunner {

    /** 配置缺省和小于 1 时的等待时间，和运行超时的缺省一致。 */
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;

    private final Duration timeout;

    private GracefulShutdownMiddleware middleware;

    /**
     * 等待时间沿用单次调用超时。配成 0 或负数时仍按 120 秒，避免停机立刻打断。
     */
    public AgentShutdown(AgentRuntimeProperties properties) {
        int seconds = properties.getTimeoutSeconds() < 1 ? DEFAULT_TIMEOUT_SECONDS : properties.getTimeoutSeconds();
        this.timeout = Duration.ofSeconds(seconds);
    }

    /**
     * 启动完成后挂上停机 hook，并记下等待时间。
     * 未完成的推理按框架默认保留，不在停机时丢掉已经生成的内容。
     */
    @Override
    public void run(ApplicationArguments args) {
        manager();
    }

    /** 每次构建 Agent 都挂上。停机后新的推理和工具调用会被打断。 */
    public GracefulShutdownMiddleware middleware() {
        if (middleware == null) {
            middleware = new GracefulShutdownMiddleware(manager());
        }
        return middleware;
    }

    /** 单例管理器。重复设置同一份超时没有副作用。 */
    private GracefulShutdownManager manager() {
        GracefulShutdownManager manager = GracefulShutdownManager.getInstance();
        manager.setConfig(new GracefulShutdownConfig(timeout, PartialReasoningPolicy.SAVE));
        return manager;
    }
}

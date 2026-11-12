package com.zhu.runtime.infrastructure.agent;

import com.zhu.runtime.config.AgentRuntimeProperties;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import java.util.logging.Logger;

/**
 * 运行层：companion 在 Sandbox 打开时构造 {@link DockerFilesystemSpec}。
 * 默认关返回 null（调用方继续 Remote）；打开时缺 Store 或 Docker 不可用则硬失败，不静默回退。
 * 快照/执行守卫留给 Harness 从 {@link DistributedStore} 注入，本类不覆写为 Noop。
 */
public final class CompanionSandboxFilesystem {

    private static final Logger LOG = Logger.getLogger(CompanionSandboxFilesystem.class.getName());

    private CompanionSandboxFilesystem() {
    }

    /**
     * {@code enabled=false} 或 sandbox 为 null → null；
     * {@code enabled=true} 则校验 Store + Docker CLI 后返回 USER 隔离的 Docker 规格。
     */
    public static SandboxFilesystemSpec createWhenEnabledOrNull(
            AgentRuntimeProperties.Sandbox sandbox, DistributedStore store) {
        return createWhenEnabledOrNull(sandbox, store, DockerCliProbe::ensureAvailable);
    }

    /**
     * 可注入 Docker 探测，供单测覆盖。探测失败须抛 {@link IllegalStateException}。
     */
    public static SandboxFilesystemSpec createWhenEnabledOrNull(
            AgentRuntimeProperties.Sandbox sandbox, DistributedStore store, Runnable dockerProbe) {
        if (sandbox == null || !sandbox.isEnabled()) {
            return null;
        }
        if (store == null) {
            LOG.warning("sandbox.enabled=true but DistributedStore missing; hard-fail, no Remote fallback");
            throw new IllegalStateException(
                    "sandbox.enabled=true but DistributedStore is missing; refusing Remote fallback");
        }
        if (dockerProbe == null) {
            throw new IllegalStateException(
                    "sandbox.enabled=true but Docker probe is null; refusing Remote fallback");
        }
        dockerProbe.run();
        String image = resolveImage(sandbox.getImage());
        LOG.info(() -> "companion sandbox filesystem enabled: image=" + image + ", isolation=USER");
        DockerFilesystemSpec spec = new DockerFilesystemSpec().image(image);
        // isolationScope 返回父类型；规格对象仍是 DockerFilesystemSpec。
        spec.isolationScope(IsolationScope.USER);
        return spec;
    }

    /** 空镜像回落推荐默认 tag。 */
    public static String resolveImage(String configured) {
        if (configured == null || configured.isBlank()) {
            return AgentRuntimeProperties.Sandbox.DEFAULT_IMAGE;
        }
        return configured.trim();
    }
}

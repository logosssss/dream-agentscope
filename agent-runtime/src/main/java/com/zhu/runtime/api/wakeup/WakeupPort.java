package com.zhu.runtime.api.wakeup;

import reactor.core.publisher.Mono;

/**
 * 运行层对外端口：唤醒。正式实现是 WakeupCoordinator；接入层 HTTP 只依赖本接口，不碰 infrastructure。
 */
public interface WakeupPort {

    /**
     * 配置是否打开唤醒面（与 Dispatcher 是否已启动一致语义：未启用则入队也会失败）。
     */
    boolean isEnabled();

    /**
     * 向进程内 Bus enqueue 一条唤醒。未启用返回 false；启用则写入后返回 true。
     */
    Mono<Boolean> enqueue(String userId, String sessionId, String agentId);
}

package com.zhu.runtime.api;

import reactor.core.publisher.Flux;

/**
 * 接入层调用执行内核的唯一入口。实现负责创建和关闭 Agent，接入层不能直接构造 HarnessAgent。
 */
public interface AgentExecutor {

    /**
     * 执行一次对话。调用结束后 Agent 已关闭。
     */
    AgentResponse execute(AgentRequest request);

    /**
     * 流式执行。返回的 Flux 被取消、完成或失败时都会关闭 Agent。调用方不要 block。
     */
    Flux<AgentStreamEvent> stream(AgentRequest request);
}

package com.zhu.runtime.api;

import reactor.core.publisher.Flux;

/**
 * 对话执行缝。正式实现是运行层 ChannelGateway（长驻 Agent）；控制器与 ChatAppService 只依赖本接口。
 * WEB 走 ChatUi；API 走同进程注册的 ApiChannel（见 docs/api-channel-落地.md）。
 */
public interface AgentExecutor {

    /**
     * WEB 同步执行一轮（ChatUi）。同 session 排队由 Gateway 负责。
     */
    AgentResponse execute(AgentRequest request);

    /**
     * API Channel 同步执行一轮。护栏与 {@link #execute} 相同，通道不同。
     */
    AgentResponse executeApi(AgentRequest request);

    /**
     * WEB 流式执行一轮（ChatUi）。调用方不要 block；取消/完成/失败时放行排水闸。
     */
    Flux<AgentStreamEvent> stream(AgentRequest request);

    /**
     * API Channel 流式执行一轮。
     */
    Flux<AgentStreamEvent> streamApi(AgentRequest request);
}

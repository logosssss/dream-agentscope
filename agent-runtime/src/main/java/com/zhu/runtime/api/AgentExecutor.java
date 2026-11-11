package com.zhu.runtime.api;

import reactor.core.publisher.Flux;

/**
 * 对话执行缝。正式实现是接入层 Channel Gateway（长驻 Agent）；控制器与 ChatAppService 只依赖本接口。
 */
public interface AgentExecutor {

    /**
     * 同步执行一轮。同 session 排队由 Gateway 负责。
     */
    AgentResponse execute(AgentRequest request);

    /**
     * 流式执行一轮。调用方不要 block；取消/完成/失败时放行排水闸。
     */
    Flux<AgentStreamEvent> stream(AgentRequest request);
}

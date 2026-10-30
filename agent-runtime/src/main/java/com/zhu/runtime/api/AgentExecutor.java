package com.zhu.runtime.api;

/**
 * 接入层调用执行内核的唯一入口。实现负责创建和关闭 Agent，接入层不能直接构造 HarnessAgent。
 */
public interface AgentExecutor {

    /**
     * 执行一次对话。调用结束后 Agent 已关闭。
     */
    AgentResponse execute(AgentRequest request);
}

package com.zhu.access.application;

import com.zhu.access.api.dto.ChatRequest;
import com.zhu.runtime.api.AgentExecutor;
import com.zhu.runtime.api.AgentRequest;
import com.zhu.runtime.api.AgentResponse;
import org.springframework.stereotype.Service;

/**
 * 把接入层请求译成 AgentRequest。这里不接触 HarnessAgent。
 */
@Service
public class ChatAppService {

    private final AgentExecutor executor;

    public ChatAppService(AgentExecutor executor) {
        this.executor = executor;
    }

    /** 同步执行一次对话。流式输出以后再从这里分开。 */
    public AgentResponse chat(ChatRequest request) {
        return executor.execute(new AgentRequest(
                request.agentId(),
                request.tenantId(),
                request.userId(),
                request.sessionId(),
                request.systemPrompt(),
                request.text()));
    }
}

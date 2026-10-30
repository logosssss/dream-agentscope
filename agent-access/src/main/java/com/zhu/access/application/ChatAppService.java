package com.zhu.access.application;

import com.zhu.access.api.dto.ChatRequest;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
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

    /**
     * 同步执行一次对话。流式输出以后再从这里分开。
     * 请求体里的租户和用户优先；空字段才用过滤器放进线程的请求头。通道留在接入层，不放进 AgentRequest。
     */
    public AgentResponse chat(ChatRequest request) {
        request.channelOrDefault();
        return executor.execute(new AgentRequest(
                request.agentId(),
                firstNonBlank(request.tenantId(), TenantContext.get()),
                firstNonBlank(request.userId(), UserContext.get()),
                request.sessionId(),
                request.systemPrompt(),
                request.text()));
    }

    /** 空白不算有效身份，否则空字符串会挡住请求头。 */
    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback == null || fallback.isBlank()) {
            return null;
        }
        return fallback;
    }
}

package com.zhu.runtime.application;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.util.Ids;
import com.zhu.runtime.api.AgentExecutor;
import com.zhu.runtime.api.AgentRequest;
import com.zhu.runtime.api.AgentResponse;
import com.zhu.runtime.api.AgentStreamEvent;
import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.runtime.infrastructure.agent.HarnessAgentFactory;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;
import java.time.Duration;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 单次执行的生命周期。身份放进 RuntimeContext，执行完无论成败都关闭 Agent。
 */
@Service
public class AgentExecutionService implements AgentExecutor {

    private final HarnessAgentFactory factory;

    private final AgentRuntimeProperties properties;

    public AgentExecutionService(HarnessAgentFactory factory, AgentRuntimeProperties properties) {
        this.factory = factory;
        this.properties = properties;
    }

    /**
     * 新建 Agent、同步等待回复、然后关闭。不要把返回的 Agent 留到方法外面。
     */
    @Override
    public AgentResponse execute(AgentRequest request) {
        require(request);
        try (HarnessAgent agent = factory.build(request)) {
            Msg reply = agent.call(request.userText(), context(request))
                    .block(Duration.ofSeconds(properties.getTimeoutSeconds()));
            String text = reply == null || reply.getTextContent() == null ? "" : reply.getTextContent();
            String runId = reply == null || reply.getId() == null ? Ids.newId() : reply.getId();
            return new AgentResponse(runId, text);
        }
    }

    /**
     * 先建好 Agent 和上下文再返回 Flux，这样请求线程上的身份已经被抄进 RuntimeContext。
     * 订阅结束时关闭 Agent。超时只作用于流，不在请求线程上 block。
     */
    @Override
    public Flux<AgentStreamEvent> stream(AgentRequest request) {
        require(request);
        HarnessAgent agent = factory.build(request);
        try {
            return agent.streamEvents(request.userText(), context(request))
                    .map(AgentStreamEvents::from)
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .doFinally(signal -> close(agent));
        } catch (RuntimeException error) {
            close(agent);
            throw error;
        }
    }

    /** 身份放进框架上下文。tenantId 不是框架字段，放在扩展项里。 */
    private static RuntimeContext context(AgentRequest request) {
        return RuntimeContext.builder()
                .userId(request.userId())
                .sessionId(request.sessionId())
                .put("tenantId", request.tenantId())
                .build();
    }

    /** 关闭失败不能盖住已经产生的回复或原始错误。 */
    private static void close(HarnessAgent agent) {
        try {
            agent.close();
        } catch (RuntimeException ignored) {
            // 关闭是收尾，不再向外抛。
        }
    }

    /** 用户、会话和输入缺一不可，否则状态无法隔离。 */
    private static void require(AgentRequest request) {
        if (request == null
                || blank(request.userId())
                || blank(request.sessionId())
                || blank(request.userText())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId, sessionId and userText are required");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

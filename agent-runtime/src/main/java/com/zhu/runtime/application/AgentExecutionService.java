package com.zhu.runtime.application;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.util.Ids;
import com.zhu.runtime.api.AgentExecutor;
import com.zhu.runtime.api.AgentRequest;
import com.zhu.runtime.api.AgentResponse;
import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.runtime.infrastructure.agent.HarnessAgentFactory;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;
import java.time.Duration;
import org.springframework.stereotype.Service;

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
            RuntimeContext context = RuntimeContext.builder()
                    .userId(request.userId())
                    .sessionId(request.sessionId())
                    .put("tenantId", request.tenantId())
                    .build();
            Msg reply = agent.call(request.userText(), context)
                    .block(Duration.ofSeconds(properties.getTimeoutSeconds()));
            String text = reply == null || reply.getTextContent() == null ? "" : reply.getTextContent();
            String runId = reply == null || reply.getId() == null ? Ids.newId() : reply.getId();
            return new AgentResponse(runId, text);
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

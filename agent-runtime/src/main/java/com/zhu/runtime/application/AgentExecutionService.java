package com.zhu.runtime.application;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.util.Ids;
import com.zhu.runtime.api.AgentExecutor;
import com.zhu.runtime.api.AgentRequest;
import com.zhu.runtime.api.AgentResponse;
import com.zhu.runtime.api.AgentStreamEvent;
import com.zhu.runtime.api.UsageMeter;
import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.runtime.domain.ModelTextGuard;
import com.zhu.runtime.infrastructure.agent.HarnessAgentFactory;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentResultEvent;
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

    private final UsageMeter usages;

    public AgentExecutionService(HarnessAgentFactory factory, AgentRuntimeProperties properties, UsageMeter usages) {
        this.factory = factory;
        this.properties = properties;
        this.usages = usages;
    }

    /**
     * 新建 Agent、同步等待回复、然后关闭。不要把返回的 Agent 留到方法外面。
     */
    @Override
    public AgentResponse execute(AgentRequest request) {
        require(request);
        try (HarnessAgent agent = factory.build(request)) {
            long started = System.nanoTime();
            Msg reply = agent.call(request.userText(), context(request))
                    .block(Duration.ofSeconds(properties.getTimeoutSeconds()));
            String text = ModelTextGuard.outbound(reply == null ? null : reply.getTextContent());
            String runId = reply == null || reply.getId() == null ? Ids.newId() : reply.getId();
            ModelCallUsage.record(
                    usages,
                    request.tenantId(),
                    request.userId(),
                    request.sessionId(),
                    runId,
                    properties.getModel(),
                    reply,
                    started);
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
        long started = System.nanoTime();
        try {
            return agent.streamEvents(request.userText(), context(request))
                    .doOnNext(event -> {
                        if (event instanceof AgentResultEvent result) {
                            Msg message = result.getResult();
                            String runId = message == null || message.getId() == null ? Ids.newId() : message.getId();
                            ModelCallUsage.record(
                                    usages,
                                    request.tenantId(),
                                    request.userId(),
                                    request.sessionId(),
                                    runId,
                                    properties.getModel(),
                                    message,
                                    started);
                        }
                    })
                    .map(AgentStreamEvents::from)
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .doFinally(signal -> close(agent));
        } catch (RuntimeException error) {
            close(agent);
            throw error;
        }
    }

    /** 身份放进框架上下文。tenantId 和 agentId 不是框架字段，放在扩展项里。 */
    private RuntimeContext context(AgentRequest request) {
        return RuntimeContext.builder()
                .userId(request.userId())
                .sessionId(request.sessionId())
                .put("tenantId", request.tenantId())
                .put("agentId", agentId(request))
                .build();
    }

    /** 请求没带标识时用运行配置。空配置仍退回 companion-v1，避免向量缺 Agent。 */
    private String agentId(AgentRequest request) {
        if (request != null && !blank(request.agentId())) {
            return request.agentId();
        }
        if (!blank(properties.getAgentId())) {
            return properties.getAgentId();
        }
        return "companion-v1";
    }

    /** 关闭失败不能盖住已经产生的回复或原始错误。 */
    private static void close(HarnessAgent agent) {
        try {
            agent.close();
        } catch (RuntimeException ignored) {
            // 关闭是收尾，不再向外抛。
        }
    }

    /** 用户、会话和输入缺一不可。过长的输入和未替换的系统提示词不能进模型。 */
    private void require(AgentRequest request) {
        if (request == null
                || blank(request.userId())
                || blank(request.sessionId())
                || blank(request.userText())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId, sessionId and userText are required");
        }
        ModelTextGuard.beforeCall(request.systemPrompt(), request.userText(), properties.getMaxUserChars());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

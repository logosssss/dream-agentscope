package com.zhu.access.application.docs;

import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.util.Ids;
import com.zhu.docs.api.DocsAgent;
import com.zhu.docs.api.DocsCompleter;
import com.zhu.runtime.api.agent.AgentExecutor;
import com.zhu.runtime.api.agent.AgentRequest;
import com.zhu.runtime.api.agent.AgentResponse;
import org.springframework.stereotype.Component;

/**
 * 把单篇问答交给 docs-v1。每次新会话，不复用聊天记录。
 * 系统提示同时放进请求，供文本护栏检查；真正约束模型的是 Agent 上的同一份原文。
 */
@Component
public class RuntimeDocsCompleter implements DocsCompleter {

    private final AgentExecutor executor;

    /** 只走 API 执行口，这样请求里的 agentId 才会被路由到文档助手。 */
    public RuntimeDocsCompleter(AgentExecutor executor) {
        this.executor = executor;
    }

    /**
     * 用户必须已经在登录上下文里。空回复当成空串，不把框架类型带回文档库。
     */
    @Override
    public String complete(String systemPrompt, String userText) {
        String tenantId = TenantContext.get() == null ? "" : TenantContext.get();
        String userId = UserContext.get();
        AgentRequest request = new AgentRequest(
                DocsAgent.ID,
                tenantId,
                userId,
                Ids.newId(),
                systemPrompt,
                userText);
        AgentResponse response = executor.executeApi(request);
        if (response == null || response.text() == null) {
            return "";
        }
        return response.text();
    }
}

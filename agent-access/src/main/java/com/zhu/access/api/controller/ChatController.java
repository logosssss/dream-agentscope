package com.zhu.access.api.controller;

import com.zhu.access.api.dto.ChatRequest;
import com.zhu.access.application.ChatAppService;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.dto.ApiResult;
import com.zhu.runtime.api.AgentResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 同步聊天入口。只做参数接入和线程上下文，执行交给 ChatAppService。
 */
@RestController
public class ChatController {

    private final ChatAppService chats;

    public ChatController(ChatAppService chats) {
        this.chats = chats;
    }

    /**
     * 执行一次对话。线程上的租户和用户在返回前清掉，避免线程池串话。
     */
    @PostMapping("/api/chat")
    public ApiResult<AgentResponse> chat(@RequestBody ChatRequest request) {
        try {
            if (request.tenantId() != null) {
                TenantContext.set(request.tenantId());
            }
            if (request.userId() != null) {
                UserContext.set(request.userId());
            }
            return ApiResult.ok(chats.chat(request));
        } finally {
            TenantContext.clear();
            UserContext.clear();
        }
    }
}

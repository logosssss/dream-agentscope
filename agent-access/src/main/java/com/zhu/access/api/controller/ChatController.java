package com.zhu.access.api.controller;

import com.zhu.access.api.dto.ChatRequest;
import com.zhu.access.application.ChatAppService;
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
     * 执行一次对话。线程上下文由 AccessIdentityFilter 写入和清理，这里不再覆盖，避免空请求体冲掉请求头。
     */
    @PostMapping("/api/chat")
    public ApiResult<AgentResponse> chat(@RequestBody ChatRequest request) {
        return ApiResult.ok(chats.chat(request));
    }
}

package com.zhu.access.api.controller.chat;

import com.zhu.access.api.dto.chat.ChatRequest;
import com.zhu.access.application.chat.ApiChatAppService;
import com.zhu.common.dto.ApiResult;
import com.zhu.runtime.api.AgentResponse;
import com.zhu.runtime.api.AgentStreamEvent;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * API Channel 对话入口。与 {@link ChatController} 分离；走 `/api/agent/chat*`。
 * 鉴权仍为 Caller + 登录会话。见 docs/api-channel-落地.md。
 */
@RestController
public class ApiAgentChatController {

    private final ApiChatAppService chats;

    public ApiAgentChatController(ApiChatAppService chats) {
        this.chats = chats;
    }

    /**
     * 同步 API 对话。
     */
    @PostMapping("/api/agent/chat")
    public ApiResult<AgentResponse> chat(@RequestBody ChatRequest request) {
        return ApiResult.ok(chats.chat(request));
    }

    /**
     * 流式 API 对话。
     */
    @PostMapping(value = "/api/agent/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentStreamEvent>> stream(@RequestBody ChatRequest request) {
        return chats.stream(request).map(event -> ServerSentEvent.<AgentStreamEvent>builder()
                .event(event.type())
                .data(event)
                .build());
    }
}

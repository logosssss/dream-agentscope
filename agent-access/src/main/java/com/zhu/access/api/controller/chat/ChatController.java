package com.zhu.access.api.controller.chat;

import com.zhu.access.api.dto.chat.ChatRequest;
import com.zhu.access.application.chat.ChatAppService;
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
 * 聊天入口。同步 JSON 和 SSE 都只做参数接入，执行交给 ChatAppService。
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

    /**
     * 流式输出。返回 Flux，不在请求线程上等待模型。身份在 ChatAppService 里同步抄走。
     */
    @PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentStreamEvent>> stream(@RequestBody ChatRequest request) {
        return chats.stream(request).map(event -> ServerSentEvent.<AgentStreamEvent>builder()
                .event(event.type())
                .data(event)
                .build());
    }
}

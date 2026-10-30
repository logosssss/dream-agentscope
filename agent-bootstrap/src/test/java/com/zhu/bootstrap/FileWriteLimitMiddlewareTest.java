package com.zhu.bootstrap;

import com.zhu.audit.api.AuditRecorder;
import com.zhu.runtime.domain.middleware.FileWriteLimitMiddleware;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 写文件超过字节上限时不进入后续执行，审计里也不放文件内容。
 */
class FileWriteLimitMiddlewareTest {

    /** 超限的写入被丢掉，同一次里的小文件和其它工具还在。 */
    @Test
    void dropsOversizedWrite() {
        List<String> recorded = new ArrayList<>();
        AuditRecorder audits = (action, userId, detail) -> recorded.add(action + " " + detail);
        FileWriteLimitMiddleware middleware = new FileWriteLimitMiddleware(4, audits);
        ToolUseBlock big = tool("write_text_file", Map.of("file_path", "a.txt", "content", "12345"));
        ToolUseBlock small = tool("insert_text_file", Map.of("content", "1234"));
        ToolUseBlock shell = tool("shell", Map.of("content", "123456789"));

        List<ToolUseBlock> forwarded = forward(middleware, List.of(big, small, shell));

        assertThat(forwarded).extracting(ToolUseBlock::getName).containsExactly("insert_text_file", "shell");
        assertThat(recorded).containsExactly("file.write.rejected tool=write_text_file");
        assertThat(recorded.get(0)).doesNotContain("12345");
    }

    /** 中文按 UTF-8 字节计，不是按字符数。 */
    @Test
    void countsUtf8Bytes() {
        FileWriteLimitMiddleware middleware = new FileWriteLimitMiddleware(3, (action, userId, detail) -> {
        });
        ToolUseBlock fits = tool("write_file", Map.of("content", "你"));
        ToolUseBlock over = tool("write_file", Map.of("content", "你a"));

        assertThat(forward(middleware, List.of(fits))).hasSize(1);
        assertThat(forward(middleware, List.of(over))).isEmpty();
    }

    private static List<ToolUseBlock> forward(FileWriteLimitMiddleware middleware, List<ToolUseBlock> calls) {
        AtomicReference<List<ToolUseBlock>> forwarded = new AtomicReference<>();
        middleware.onActing(
                        mock(Agent.class),
                        RuntimeContext.builder().userId("user-a").sessionId("s1").build(),
                        new ActingInput(calls),
                        input -> {
                            forwarded.set(input.toolCalls());
                            return Flux.empty();
                        })
                .blockLast();
        return forwarded.get();
    }

    private static ToolUseBlock tool(String name, Map<String, Object> input) {
        return new ToolUseBlock.Builder().id(name).name(name).input(input).build();
    }
}

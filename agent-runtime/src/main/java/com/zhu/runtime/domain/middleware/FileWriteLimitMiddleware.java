package com.zhu.runtime.domain.middleware;

import com.zhu.audit.api.event.AuditRecorder;
import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.runtime.domain.CallIdentity;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 限制一次写文件的体积。大文件进沙箱后会把 MySQL 快照撑大，所以在执行前丢掉超限调用。
 * 顺序靠前，审批中间件看不到已经被丢掉的写入。上限每次从运行配置读取，便于热更。
 */
@Component
public class FileWriteLimitMiddleware implements MiddlewareBase {

    /** 指南里的单文件上限。配置小于 1 时仍用这个值，避免配成 0 后不再限制。 */
    static final int DEFAULT_MAX_BYTES = 5 * 1024 * 1024;

    private static final Set<String> WRITE_TOOLS = Set.of("write_file", "write_text_file", "insert_text_file");

    private final AgentRuntimeProperties properties;

    private final Integer fixedMaxBytes;

    private final AuditRecorder audits;

    /**
     * 正式装配：上限跟 Nacos 热更走。
     */
    @Autowired
    public FileWriteLimitMiddleware(AgentRuntimeProperties properties, AuditRecorder audits) {
        this.properties = properties;
        this.fixedMaxBytes = null;
        this.audits = audits;
    }

    /**
     * 测试用固定上限。
     *
     * @param maxBytes 单次 content 的字节上限。未配置时是 5MB
     * @param audits   只记工具名和字节数，不记文件内容
     */
    public FileWriteLimitMiddleware(int maxBytes, AuditRecorder audits) {
        this.properties = null;
        this.fixedMaxBytes = maxBytes < 1 ? DEFAULT_MAX_BYTES : maxBytes;
        this.audits = audits;
    }

    /** 当前生效的字节上限。 */
    private int maxBytes() {
        if (fixedMaxBytes != null) {
            return fixedMaxBytes;
        }
        int configured = properties.getMaxWriteBytes();
        return configured < 1 ? DEFAULT_MAX_BYTES : configured;
    }

    /** 比默认顺序更早执行，先丢掉超限写入，再进入审批。 */
    @Override
    public int order() {
        return 0;
    }

    /**
     * 只检查写文件工具的 content。其它工具原样留下。
     * 超限的调用不交给后续中间件，因此不会写入工作区。
     */
    @Override
    public Flux<AgentEvent> onActing(
            Agent agent,
            RuntimeContext context,
            ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {
        List<ToolUseBlock> calls = input.toolCalls();
        if (calls == null || calls.isEmpty()) {
            return next.apply(input);
        }
        CallIdentity identity = CallIdentity.from(context);
        String actor = identity.userId() == null ? "" : identity.userId();
        List<ToolUseBlock> kept = new ArrayList<>(calls.size());
        for (ToolUseBlock use : calls) {
            if (oversized(use)) {
                audits.record("file.write.rejected", actor, "tool=" + use.getName());
                continue;
            }
            kept.add(use);
        }
        return next.apply(new ActingInput(kept));
    }

    /** 不是写文件，或内容不超过上限。content 缺失时不拦。 */
    private boolean oversized(ToolUseBlock use) {
        if (use == null || use.getName() == null || !WRITE_TOOLS.contains(use.getName())) {
            return false;
        }
        Map<String, Object> input = use.getInput();
        if (input == null) {
            return false;
        }
        return exceeds(input.get("content"));
    }

    /** 文本按 UTF-8 字节计，字节数组按长度计。其它类型这里不估。 */
    private boolean exceeds(Object content) {
        int maxBytes = maxBytes();
        if (content instanceof byte[] bytes) {
            return bytes.length > maxBytes;
        }
        if (content instanceof String text) {
            return textTooLarge(text, maxBytes);
        }
        return false;
    }

    /**
     * 字符数已经超过上限时，UTF-8 至少一样大，不必再逐字数。
     * 字符数很少时，即使每个字符 4 字节也放得下。
     */
    static boolean textTooLarge(String text, int maxBytes) {
        if (text.length() > maxBytes) {
            return true;
        }
        if (text.length() <= maxBytes / 4) {
            return false;
        }
        int bytes = 0;
        for (int i = 0; i < text.length();) {
            char current = text.charAt(i++);
            if (current <= 0x7F) {
                bytes += 1;
            } else if (current <= 0x7FF) {
                bytes += 2;
            } else if (Character.isHighSurrogate(current)
                    && i < text.length()
                    && Character.isLowSurrogate(text.charAt(i))) {
                bytes += 4;
                i++;
            } else {
                bytes += 3;
            }
            if (bytes > maxBytes) {
                return true;
            }
        }
        return false;
    }
}

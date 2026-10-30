package com.zhu.bootstrap;

import com.zhu.common.exception.BusinessException;
import com.zhu.semantic.application.PromptAppService;
import com.zhu.semantic.infrastructure.nacos.PromptSource;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 外部提示词缺失或失败时必须退回调用方的兜底，不能变成空串。
 */
class PromptServiceTest {

    /** 模板里的占位符按调用方变量替换，未给出的占位符保留。 */
    @Test
    void rendersKnownVariables() {
        PromptAppService prompts = new PromptAppService(key -> "你好 {{name}}，{{unknown}}");

        String text = prompts.get("prompt.platform.system", Map.of("name", "张三"), "fallback");

        assertThat(text).isEqualTo("你好 张三，{{unknown}}");
    }

    /** 来源返回空白，或直接抛错，都用 fallback，不把异常抛给对话。 */
    @Test
    void blankOrFailureUsesFallback() {
        PromptAppService blank = new PromptAppService(key -> "  ");
        PromptAppService failed = new PromptAppService(key -> {
            throw new IllegalStateException("nacos down");
        });

        assertThat(blank.get("prompt.platform.system", Map.of(), "keep")).isEqualTo("keep");
        assertThat(failed.get("prompt.platform.system", Map.of(), "keep")).isEqualTo("keep");
    }

    /** 兜底本身为空时拒绝，避免空提示词进入模型。 */
    @Test
    void blankFallbackIsRejected() {
        PromptSource source = key -> "from-source";
        PromptAppService prompts = new PromptAppService(source);

        assertThatThrownBy(() -> prompts.get("prompt.platform.system", Map.of(), " "))
                .isInstanceOf(BusinessException.class);
    }
}

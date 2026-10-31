package com.zhu.bootstrap;

import com.zhu.semantic.api.PlatformPrompt;
import com.zhu.semantic.infrastructure.prompt.PromptTemplateCheck;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 提示词模板在进仓库时就要能校验。不连接 Nacos。
 */
class PromptTemplateCheckTest {

    /** 系统提示词没有登记变量，正文里不能出现占位符。 */
    @Test
    void systemPromptRejectsUnknownVariable() {
        assertThatCode(() -> PromptTemplateCheck.checkRegistered("prompt.platform.system", "你好", false))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> PromptTemplateCheck.checkRegistered(
                        "prompt.platform.system", "你好 {{name}}", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("prompt variable is unknown");
    }

    /** 没闭合的占位符拒绝。单括号留给 JSON 正文。 */
    @Test
    void incompletePlaceholderIsRejected() {
        assertThatThrownBy(() -> PromptTemplateCheck.check(
                        "prompt.platform.system", "你好 {{name", Set.of("name"), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("prompt placeholder is incomplete");
    }

    /** 登记过的变量可以替换。JSON 模板在替换后必须能解析。 */
    @Test
    void jsonPromptMustParseAfterRender() {
        assertThatCode(() -> PromptTemplateCheck.check(
                        "prompt.review.output", "{\"summary\":\"{{text}}\"}", Set.of("text"), true))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> PromptTemplateCheck.check(
                        "prompt.review.output", "{\"summary\":\"{{text}}\"", Set.of("text"), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("prompt json is invalid");
    }

    /** 目录里的 .md 和 .json 都要过。其它后缀拒绝。 */
    @Test
    void directoryRejectsUnknownFile(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("prompt.platform.system.md"), "你好");
        assertThatCode(() -> PromptTemplateCheck.checkDirectory(dir)).doesNotThrowAnyException();
        Files.writeString(dir.resolve("notes.txt"), "x");
        assertThatThrownBy(() -> PromptTemplateCheck.checkDirectory(dir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("prompt file is invalid");
    }

    /** 已经放进 classpath 的模板必须通过。目录还没有时跳过，不连 Nacos。 */
    @Test
    void classpathPromptsPass() throws URISyntaxException {
        URL root = PlatformPrompt.class.getResource("/prompts");
        if (root == null) {
            return;
        }
        PromptTemplateCheck.checkDirectory(Path.of(root.toURI()));
    }
}

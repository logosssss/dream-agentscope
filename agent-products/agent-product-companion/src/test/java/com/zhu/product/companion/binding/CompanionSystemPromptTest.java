package com.zhu.product.companion.binding;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.semantic.api.prompt.PlatformPrompt;
import com.zhu.semantic.api.prompt.PromptService;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 长驻 Agent 的系统提示词来自外部来源（Nacos），取不到才用产品兜底。
 *
 * <p>回归背景：早先 {@code customize} 直接取 {@code promptFallback(null)}，
 * Nacos 提示词热更、模板校验与发布对线上人格零效果，改的只是接入层那份只用于校验的字符串。
 */
class CompanionSystemPromptTest {

    /** 外部来源给了模板就用它，不再用硬编码兜底。 */
    @Test
    void usesExternalTemplateWhenPresent() {
        CompanionResidentAgentBinding binding = binding((key, variables, fallback) -> "外部模板");

        assertThat(binding.systemPrompt()).isEqualTo("外部模板");
    }

    /** 外部来源缺失时退回产品兜底，且键就是平台约定的那条。 */
    @Test
    void fallsBackToProductPrompt() {
        CompanionResidentAgentBinding binding =
                binding((key, variables, fallback) -> fallback);

        assertThat(binding.systemPrompt()).isEqualTo(PlatformPrompt.SYSTEM_FALLBACK);
        assertThat(binding.systemPrompt()).isNotBlank();
    }

    /** 读取口缺席时也必须有提示词，不能把空串交给模型。 */
    @Test
    void missingPromptServiceStillCarriesPrompt() {
        CompanionResidentAgentBinding binding = binding(null);

        assertThat(binding.systemPrompt()).isEqualTo(PlatformPrompt.SYSTEM_FALLBACK);
    }

    /** 装配时问的就是平台系统提示词键，不传变量。 */
    @Test
    void asksForThePlatformSystemKey() {
        StringBuilder seen = new StringBuilder();
        CompanionResidentAgentBinding binding = binding((key, variables, fallback) -> {
            seen.append(key).append('|').append(variables.size());
            return fallback;
        });

        binding.systemPrompt();

        assertThat(seen.toString()).isEqualTo(PlatformPrompt.SYSTEM_KEY + "|0");
    }

    private static CompanionResidentAgentBinding binding(PromptService prompts) {
        return new CompanionResidentAgentBinding(
                new AgentRuntimeProperties(),
                null,
                null,
                null,
                null,
                List.of(),
                prompts);
    }
}

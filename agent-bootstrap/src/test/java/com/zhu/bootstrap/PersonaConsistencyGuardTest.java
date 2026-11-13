package com.zhu.bootstrap;

import com.zhu.runtime.domain.PersonaConsistencyGuard;
import com.zhu.semantic.config.product.CompanionPersona;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 人设漂移只看候选正文。基线里的「不是恋人」不能把自己盖掉。
 */
class PersonaConsistencyGuardTest {

    /** 未启用时原样过占位符。自称恋人也不换。 */
    @Test
    void disabledKeepsLoverClaim() {
        assertThat(PersonaConsistencyGuard.finalize("我是你的恋人", false, "基线")).isEqualTo("我是你的恋人");
        assertThat(PersonaConsistencyGuard.finalize("结果 {{name}}", false, "基线")).isEmpty();
    }

    /** 启用后恋人、助手或替人决定都换成基线。 */
    @Test
    void enabledHitsAreReplaced() {
        assertThat(PersonaConsistencyGuard.finalize("我是你的恋人", true, "基线")).isEqualTo("基线");
        assertThat(PersonaConsistencyGuard.finalize("我是工具助手", true, "基线")).isEqualTo("基线");
        assertThat(PersonaConsistencyGuard.finalize("我替你决定吧", true, "基线")).isEqualTo("基线");
    }

    /** 启用时干净回复留下。空串和被丢掉的占位符不拦。 */
    @Test
    void enabledCleanReplyStays() {
        assertThat(PersonaConsistencyGuard.finalize("今天去散步吗", true, "基线")).isEqualTo("今天去散步吗");
        assertThat(PersonaConsistencyGuard.finalize("", true, "基线")).isEmpty();
        assertThat(PersonaConsistencyGuard.finalize(null, true, "基线")).isEmpty();
        assertThat(PersonaConsistencyGuard.finalize("结果 {{name}}", true, "基线")).isEmpty();
    }

    /** 基线空白时用代码兜底。盖掉后不再拿基线去过词表。 */
    @Test
    void replacementUsesFallbackAndIsNotRechecked() {
        String baseline = PersonaConsistencyGuard.baseline(new CompanionPersona());
        assertThat(PersonaConsistencyGuard.finalize("我是你的恋人", true, " "))
                .isEqualTo(PersonaConsistencyGuard.baseline(null));
        assertThat(PersonaConsistencyGuard.finalize("我是你的恋人", true, baseline)).isEqualTo(baseline);
        assertThat(baseline).contains("恋人");
        assertThat(baseline).isEqualTo(PersonaConsistencyGuard.baseline(null));
    }
}

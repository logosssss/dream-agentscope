package com.zhu.bootstrap;

import com.zhu.semantic.config.safety.ClasspathCrisisPrompt;
import com.zhu.semantic.contract.safety.CrisisPrompt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 危机话术来自 classpath。文件和代码兜底必须一致。
 */
class ClasspathCrisisPromptTest {

    /** 读到的正文非空，并且和锁定兜底相同。 */
    @Test
    void classpathMatchesFallback() {
        ClasspathCrisisPrompt prompt = new ClasspathCrisisPrompt();
        assertThat(prompt.text()).isEqualTo(CrisisPrompt.FALLBACK);
        assertThat(prompt.text()).isNotBlank();
        assertThat(prompt.text()).doesNotContain("{{");
    }
}

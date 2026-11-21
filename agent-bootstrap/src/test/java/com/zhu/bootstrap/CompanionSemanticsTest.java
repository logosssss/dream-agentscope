package com.zhu.bootstrap;

import com.zhu.product.companion.semantic.CompanionSemantics;
import com.zhu.semantic.api.prompt.PlatformPrompt;
import com.zhu.semantic.api.tool.NoToolCatalog;
import com.zhu.semantic.contract.tool.ToolDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 搭子产品语义给出非空标识、轮数和提示词兜底。工具目录为空。
 */
class CompanionSemanticsTest {

    /** 无效配置退回缺省，未知提示词键也不返回空串。 */
    @Test
    void blanksUseDefaults() {
        CompanionSemantics semantics = new CompanionSemantics(" ", 0, new NoToolCatalog());

        assertThat(semantics.agentId()).isEqualTo("companion-v1");
        assertThat(semantics.completion().maxIterations()).isEqualTo(20);
        assertThat(semantics.tools()).isEmpty();
        assertThat(semantics.promptFallback(PlatformPrompt.SYSTEM_KEY)).isEqualTo(PlatformPrompt.SYSTEM_FALLBACK);
        assertThat(semantics.promptFallback(null)).isEqualTo(PlatformPrompt.SYSTEM_FALLBACK);
    }

    /** 目录里的工具原样交出，不在这里执行。 */
    @Test
    void toolsComeFromCatalog() {
        ToolDescriptor shell = new ToolDescriptor("shell", "Shell", "run");
        CompanionSemantics semantics = new CompanionSemantics("agent-a", 8, () -> List.of(shell));

        assertThat(semantics.agentId()).isEqualTo("agent-a");
        assertThat(semantics.completion().maxIterations()).isEqualTo(8);
        assertThat(semantics.tools()).containsExactly(shell);
    }
}

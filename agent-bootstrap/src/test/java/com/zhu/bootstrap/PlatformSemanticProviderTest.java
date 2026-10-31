package com.zhu.bootstrap;

import com.zhu.semantic.api.NoToolCatalog;
import com.zhu.semantic.api.PlatformPrompt;
import com.zhu.semantic.config.PlatformSemanticProvider;
import com.zhu.semantic.contract.ToolDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 默认产品语义给出非空标识、轮数和提示词兜底。工具目录为空。
 */
class PlatformSemanticProviderTest {

    /** 无效配置退回缺省，未知提示词键也不返回空串。 */
    @Test
    void blanksUseDefaults() {
        PlatformSemanticProvider provider = new PlatformSemanticProvider(" ", 0, new NoToolCatalog());

        assertThat(provider.agentId()).isEqualTo("product-agent");
        assertThat(provider.completion().maxIterations()).isEqualTo(20);
        assertThat(provider.tools()).isEmpty();
        assertThat(provider.promptFallback(PlatformPrompt.SYSTEM_KEY)).isEqualTo(".");
        assertThat(provider.promptFallback(null)).isEqualTo(".");
    }

    /** 目录里的工具原样交出，不在这里执行。 */
    @Test
    void toolsComeFromCatalog() {
        ToolDescriptor shell = new ToolDescriptor("shell", "Shell", "run");
        PlatformSemanticProvider provider = new PlatformSemanticProvider(
                "agent-a",
                8,
                () -> List.of(shell));

        assertThat(provider.agentId()).isEqualTo("agent-a");
        assertThat(provider.completion().maxIterations()).isEqualTo(8);
        assertThat(provider.tools()).containsExactly(shell);
    }
}

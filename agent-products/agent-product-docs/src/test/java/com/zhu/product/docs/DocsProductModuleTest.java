package com.zhu.product.docs;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.docs.api.DocsAgent;
import com.zhu.docs.api.DocsAskPrompt;
import com.zhu.runtime.application.turn.ResolvedChatModel;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 文档产品不是 main，提示词与文档库是同一份原文。
 */
class DocsProductModuleTest {

    /** 不挂工具，一轮结束。模型对象只为了能构造，这里不调用。 */
    @Test
    void staysOffTheMainPath() {
        DocsProductModule module = new DocsProductModule(Mockito.mock(ResolvedChatModel.class));

        assertThat(module.mainAgent()).isFalse();
        assertThat(module.agentId()).isEqualTo(DocsAgent.ID);
        assertThat(module.tools()).isEmpty();
        assertThat(module.completion().maxIterations()).isEqualTo(1);
        assertThat(module.promptFallback(null)).isEqualTo(DocsAskPrompt.SYSTEM);
        assertThat(module.promptFallback("other")).isEqualTo(DocsAskPrompt.SYSTEM);
    }
}

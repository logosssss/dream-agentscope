package com.zhu.bootstrap;

import com.zhu.audit.api.ModelConfigLookup;
import com.zhu.runtime.application.ResolvedChatModel;
import com.zhu.runtime.config.AgentRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 有启用配置用配置，没有再用进程里的模型名。不连接 MySQL。
 */
class ResolvedChatModelTest {

    /** 目录关闭或没有行时保持进程配置。 */
    @Test
    void usesRegisteredModelWhenPresent() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setModel("dashscope:qwen-plus");
        ObjectProvider<ModelConfigLookup> lookups = mock(ObjectProvider.class);
        when(lookups.getIfAvailable()).thenReturn(null);
        assertThat(new ResolvedChatModel(properties, lookups).resolve("t")).isEqualTo("dashscope:qwen-plus");

        ModelConfigLookup lookup = mock(ModelConfigLookup.class);
        when(lookups.getIfAvailable()).thenReturn(lookup);
        when(lookup.resolveModel("t")).thenReturn(null);
        assertThat(new ResolvedChatModel(properties, lookups).resolve("t")).isEqualTo("dashscope:qwen-plus");

        when(lookup.resolveModel("t")).thenReturn("dashscope:qwen-max");
        assertThat(new ResolvedChatModel(properties, lookups).resolve("t")).isEqualTo("dashscope:qwen-max");
    }
}
